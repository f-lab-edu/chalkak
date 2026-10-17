import http from 'k6/http';
import exec from 'k6/execution';
import {check, sleep} from 'k6';
import {Counter} from 'k6/metrics';

const BASE_URL = 'http://localhost:8080';
// k6는 host 네트워크로 실행되므로 LocalStack에 localhost로 접근한다
const SQS_ENDPOINT = 'http://localhost:4566';
const BID_QUEUE_URL = `${SQS_ENDPOINT}/000000000000/bid-queue.fifo`;
// 입찰은 큐에 접수만 되고 컨슈머가 나중에 처리하므로, 검증 전에 큐가 빌 때까지 기다린다
const QUEUE_DRAIN_TIMEOUT_SECONDS = 300;

// 테스트 대상 경매는 모두 입찰 기록 없음, 시작가 10,000원, 마감 2026-10-30 23:59:59
// hot: smoke·concurrent에서 사용하는 경매 1개
const HOT_AUCTION_ID = 22046;
// spread: 입찰이 여러 경매로 나뉘는 경우에 사용하는 경매 5개 (hot 경매와 겹치지 않는다)
const SPREAD_AUCTION_IDS = [175650, 192002, 203490, 220628, 299503];
const START_PRICE = 10000;
const BID_STEP = 100;

// test001 ~ test005 계정 사용 (비밀번호: test{번호}123!, 포인트 5천만 원씩)
const BIDDER_COUNT = 5;
const INITIAL_POINT = 50000000;

const SMOKE_ITERATIONS = 30;
const CONCURRENT_DURATION = '30s';

// 실행: k6 run -e MODE=smoke|concurrent|spread|rps|mixed -e VUS=20 -e RATE=200 -e PRE_VUS=50 -e MAX_VUS=500 -e LIST_RATE=20 -e LIST_PRE_VUS=100 -e LIST_MAX_VUS=500 script.js
// (기본값 smoke, VUS는 concurrent·spread에서만, RATE·PRE_VUS·MAX_VUS는 rps·mixed에서만, LIST_로 시작하는 값은 mixed에서만 사용)
const MODE = __ENV.MODE || 'smoke';
const CONCURRENT_VUS = Number(__ENV.VUS || 20);
// rps: 초당 시작하는 반복 수. 반복 1번 = 현재가 조회 + 입찰 2요청이라 초당 HTTP 요청 수는 RATE의 2배
const RPS_RATE = Number(__ENV.RATE || 50);
const RPS_DURATION = '30s';
// rps: 필요한 VU ≈ RATE × 반복 1번에 걸리는 시간(초). 서버가 느려지면 필요한 VU가 늘고, MAX_VUS를 넘으면 k6가 dropped_iterations로 기록한다
const RPS_PRE_VUS = Number(__ENV.PRE_VUS || 50);
const RPS_MAX_VUS = Number(__ENV.MAX_VUS || 500);
// mixed: 입찰과 함께 초당 시작하는 경매 목록 조회 수
const LIST_RATE = Number(__ENV.LIST_RATE || 20);
// mixed: 시나리오끼리 VU를 나눠 쓰지 않으므로 입찰의 MAX_VUS와 따로 둔다. 목록 조회는 요청 속도가 낮아 VU가 적게 필요하다
const LIST_PRE_VUS = Number(__ENV.LIST_PRE_VUS || 100);
const LIST_MAX_VUS = Number(__ENV.LIST_MAX_VUS || 500);

// 입찰 API는 큐에 접수만 하고 202를 응답하므로, 성공·실패는 응답이 아니라 bid_requests와 BID_CONSUME 로그로 확인한다
const bidAccepted = new Counter('bid_accepted');
const bidTimeout = new Counter('bid_timeout');
const bidUnexpected = new Counter('bid_unexpected');

const SCENARIOS = {
  // VU 1개가 앞 요청의 응답을 받은 뒤 다음 요청을 보내므로 요청이 겹치지 않는다
  smoke: {
    executor: 'per-vu-iterations',
    exec: 'smokeBid',
    vus: 1,
    iterations: SMOKE_ITERATIONS,
  },
  // VU 여러 개가 대기 없이 같은 경매에 입찰해서 경매 행 락 경합을 만든다
  concurrent: {
    executor: 'constant-vus',
    exec: 'concurrentBid',
    vus: CONCURRENT_VUS,
    duration: CONCURRENT_DURATION,
  },
  // VU 여러 개가 경매 5개에 나눠 입찰해서, 서로 다른 경매의 트랜잭션이 같은 입찰자의 포인트 행을 동시에 잠그게 만든다
  spread: {
    executor: 'constant-vus',
    exec: 'spreadBid',
    vus: CONCURRENT_VUS,
    duration: CONCURRENT_DURATION,
  },
  // 응답을 기다리지 않고 초당 정해진 수만큼 hot 경매에 입찰을 시작한다. 서버가 느려져도 요청 속도는 줄지 않는다
  rps: {
    executor: 'constant-arrival-rate',
    exec: 'concurrentBid',
    rate: RPS_RATE,
    timeUnit: '1s',
    duration: RPS_DURATION,
    preAllocatedVUs: RPS_PRE_VUS,
    maxVUs: RPS_MAX_VUS,
  },
};

// mixed: rps 입찰과 같은 시간 동안 경매 목록을 일정한 속도로 조회해서, 입찰이 몰릴 때 다른 API가 얼마나 느려지는지 본다
// 목록 조회도 응답을 기다리지 않고 시작하므로, 서버가 느려지면 응답 시간에 그대로 드러난다
const MIXED_SCENARIOS = {
  rps: SCENARIOS.rps,
  list: {
    executor: 'constant-arrival-rate',
    exec: 'listAuctions',
    rate: LIST_RATE,
    timeUnit: '1s',
    duration: RPS_DURATION,
    preAllocatedVUs: LIST_PRE_VUS,
    maxVUs: LIST_MAX_VUS,
  },
};

const THRESHOLDS = {
  smoke: {
    bid_accepted: [`count==${SMOKE_ITERATIONS}`],
    bid_timeout: ['count==0'],
  },
  // 최대 부하까지 올려서 타임아웃이 나더라도 정합성이 지켜지는지 보는 것이 목적이라 타임아웃은 실패 조건에서 뺀다
  // 타임아웃 건은 큐에 접수됐을 수 있으므로 bid_requests 행 수와 대조한다
  concurrent: {
    bid_accepted: ['count>0'],
  },
  spread: {
    bid_accepted: ['count>0'],
  },
  rps: {
    bid_accepted: ['count>0'],
  },
  mixed: {
    bid_accepted: ['count>0'],
  },
};

if (!SCENARIOS[MODE] && MODE !== 'mixed') {
  throw new Error(`알 수 없는 MODE: ${MODE} (smoke | concurrent | spread | rps | mixed)`);
}

const AUCTION_IDS = MODE === 'spread' ? SPREAD_AUCTION_IDS : [HOT_AUCTION_ID];
const RESET_SQL = MODE === 'spread' ? 'reset_spread_test.sql' : 'reset_bid_test.sql';
if (!Number.isInteger(CONCURRENT_VUS) || CONCURRENT_VUS < 1) {
  throw new Error(`VUS는 1 이상의 정수여야 합니다: ${__ENV.VUS}`);
}
if (!Number.isInteger(RPS_RATE) || RPS_RATE < 1) {
  throw new Error(`RATE는 1 이상의 정수여야 합니다: ${__ENV.RATE}`);
}
if (!Number.isInteger(LIST_RATE) || LIST_RATE < 1) {
  throw new Error(`LIST_RATE는 1 이상의 정수여야 합니다: ${__ENV.LIST_RATE}`);
}
if (!Number.isInteger(LIST_PRE_VUS) || LIST_PRE_VUS < 1 || !Number.isInteger(LIST_MAX_VUS) || LIST_MAX_VUS < LIST_PRE_VUS) {
  throw new Error(`LIST_PRE_VUS는 1 이상, LIST_MAX_VUS는 LIST_PRE_VUS 이상의 정수여야 합니다: LIST_PRE_VUS=${__ENV.LIST_PRE_VUS}, LIST_MAX_VUS=${__ENV.LIST_MAX_VUS}`);
}
if (!Number.isInteger(RPS_PRE_VUS) || RPS_PRE_VUS < 1 || !Number.isInteger(RPS_MAX_VUS) || RPS_MAX_VUS < RPS_PRE_VUS) {
  throw new Error(`PRE_VUS는 1 이상, MAX_VUS는 PRE_VUS 이상의 정수여야 합니다: PRE_VUS=${__ENV.PRE_VUS}, MAX_VUS=${__ENV.MAX_VUS}`);
}

export const options = {
  // 기본 60초로는 큐가 다 비기 전에 teardown이 끊길 수 있다
  teardownTimeout: `${QUEUE_DRAIN_TIMEOUT_SECONDS + 60}s`,
  scenarios: MODE === 'mixed' ? MIXED_SCENARIOS : {[MODE]: SCENARIOS[MODE]},
  thresholds: Object.assign({}, THRESHOLDS[MODE], {
    bid_unexpected: ['count==0'],
    checks: ['rate==1'],
    // http_req_duration은 로그인·입찰·조회를 모두 합산하므로, 요청 종류별로 나눠 summary에 출력한다
    'http_req_duration{api:login}': [],
    'http_req_duration{api:read}': [],
    'http_req_duration{api:bid}': [],
    'http_req_duration{api:verify}': [],
    'http_req_duration{api:list}': [],
  }),
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

function testAccount(index) {
  const number = String(index + 1).padStart(3, '0');
  return {email: `test${number}@test.com`, password: `test${number}123!`};
}

function login(index) {
  const account = testAccount(index);
  const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify(account), {
    headers: {'Content-Type': 'application/json'},
    // 이전 계정의 쿠키가 붙으면 서버가 XSRF-TOKEN을 새로 발급하지 않으므로, 로그인마다 빈 쿠키 저장소를 사용한다
    jar: new http.CookieJar(),
    tags: {api: 'login'},
  });

  if (res.status !== 200 || !res.cookies.JSESSIONID || !res.cookies['XSRF-TOKEN']) {
    exec.test.abort(`로그인 실패: ${account.email} status=${res.status}`);
  }

  return {
    email: account.email,
    jsessionId: res.cookies.JSESSIONID[0].value,
    xsrfToken: res.cookies['XSRF-TOKEN'][0].value,
  };
}

// 요청마다 해당 계정의 세션을 명시적으로 지정 (VU의 쿠키 저장소에 남은 다른 계정 쿠키를 덮어씀)
function authParams(session) {
  return {
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': session.xsrfToken,
    },
    cookies: {
      'JSESSIONID': {value: session.jsessionId, replace: true},
      'XSRF-TOKEN': {value: session.xsrfToken, replace: true},
    },
  };
}

function fetchCurrentPrice(auctionId, api) {
  const res = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`, {tags: {api}});
  if (!check(res, {'경매 조회 200': (r) => r.status === 200})) {
    return null;
  }
  return res.json('currentPrice');
}

function fetchPoint(session) {
  const res = http.get(`${BASE_URL}/api/v1/points`, Object.assign(authParams(session), {tags: {api: 'verify'}}));
  return {
    email: session.email,
    available: res.json('availableAmount'),
    locked: res.json('lockedAmount'),
  };
}

function bid(session, auctionId, amount) {
  const res = http.post(
    `${BASE_URL}/api/v1/auctions/${auctionId}/bids`,
    JSON.stringify({bidAmount: amount}),
    Object.assign(authParams(session), {timeout: '10s', tags: {api: 'bid'}}),
  );

  if (res.status === 202) {
    bidAccepted.add(1);
  } else if (res.status === 0) {
    // 클라이언트 타임아웃 또는 연결 실패: 큐에는 접수됐을 수 있어서 금액을 남겨두고 DB와 대조한다
    bidTimeout.add(1);
    console.error(`TIMEOUT_BID auction=${auctionId} bidder=${session.email} amount=${amount} error=${res.error}`);
  } else {
    bidUnexpected.add(1);
    console.error(`UNEXPECTED_BID auction=${auctionId} bidder=${session.email} amount=${amount} status=${res.status} body=${res.body}`);
  }
}

export function setup() {
  // 이전 테스트의 입찰이 남아 있으면 기대값과 불변식 검증이 모두 틀어지므로, 리셋 여부를 먼저 확인한다
  AUCTION_IDS.forEach((auctionId) => {
    const currentPrice = fetchCurrentPrice(auctionId, 'verify');
    if (currentPrice !== START_PRICE) {
      exec.test.abort(`경매 ${auctionId} 현재가가 ${currentPrice}원입니다. ${RESET_SQL}을 먼저 실행하세요.`);
    }
  });

  // 카운터가 한 번도 기록되지 않으면 threshold 판정에서 빠질 수 있어서 0으로 먼저 기록해 둔다
  bidAccepted.add(0);
  bidTimeout.add(0);
  bidUnexpected.add(0);

  const sessions = [];
  for (let i = 0; i < BIDDER_COUNT; i++) {
    sessions.push(login(i));
  }
  return sessions;
}

// test001 → test001 → test002 → test002 → ... 순서로 한 사람이 2번씩 연속 입찰
// 최초 입찰, 같은 입찰자 재입찰, 다른 입찰자 입찰 세 경로가 모두 실행된다
function smokeBidderIndex(iteration) {
  return Math.floor(iteration / 2) % BIDDER_COUNT;
}

export function smokeBid(sessions) {
  const iteration = exec.scenario.iterationInTest;
  const session = sessions[smokeBidderIndex(iteration)];
  const amount = START_PRICE + BID_STEP * (iteration + 1);
  bid(session, HOT_AUCTION_ID, amount);
}

// 사용자처럼 현재가를 보고 한 단계 높여 입찰한다
// 여러 VU가 같은 현재가를 읽으므로 같은 금액끼리 경쟁하고, 락을 먼저 얻은 한 건만 성공한다
export function concurrentBid(sessions) {
  const session = sessions[Math.floor(Math.random() * BIDDER_COUNT)];
  bidAboveCurrentPrice(session, HOT_AUCTION_ID);
}

// 경매와 입찰자를 각각 랜덤으로 고르므로, 한 입찰자가 여러 경매에서 동시에 앞지르거나 밀려난다
// 다른 입찰자를 앞지르는 입찰은 두 사람의 포인트 행을 함께 잠그므로, 경매끼리 같은 포인트 행을 두고 경합한다
export function spreadBid(sessions) {
  const auctionId = SPREAD_AUCTION_IDS[Math.floor(Math.random() * SPREAD_AUCTION_IDS.length)];
  const session = sessions[Math.floor(Math.random() * BIDDER_COUNT)];
  bidAboveCurrentPrice(session, auctionId);
}

export function listAuctions() {
  const res = http.get(`${BASE_URL}/api/v1/auctions`, {tags: {api: 'list'}});
  check(res, {'경매 목록 조회 200': (r) => r.status === 200});
}

function bidAboveCurrentPrice(session, auctionId) {
  const currentPrice = fetchCurrentPrice(auctionId, 'read');
  if (currentPrice === null) {
    return;
  }
  bid(session, auctionId, currentPrice + BID_STEP);
}

// 메시지는 컨슈머가 처리를 마친 뒤 삭제되므로, 대기(Messages)와 처리 중(NotVisible)이 모두 0이면 처리가 끝난 것이다
function queueDepth() {
  const res = http.post(SQS_ENDPOINT, JSON.stringify({
    QueueUrl: BID_QUEUE_URL,
    AttributeNames: ['ApproximateNumberOfMessages', 'ApproximateNumberOfMessagesNotVisible'],
  }), {
    headers: {
      'Content-Type': 'application/x-amz-json-1.0',
      'X-Amz-Target': 'AmazonSQS.GetQueueAttributes',
      // LocalStack은 서명을 검증하지 않지만, 큐가 있는 리전을 이 헤더에서 읽는다
      'Authorization': 'AWS4-HMAC-SHA256 Credential=test/20260101/ap-northeast-2/sqs/aws4_request, SignedHeaders=host, Signature=test',
    },
    tags: {api: 'verify'},
  });
  if (res.status !== 200) {
    exec.test.abort(`큐 상태 조회 실패: status=${res.status} body=${res.body}`);
  }
  const attributes = res.json('Attributes');
  return Number(attributes.ApproximateNumberOfMessages) + Number(attributes.ApproximateNumberOfMessagesNotVisible);
}

function waitForQueueDrain() {
  const startedAt = Date.now();
  let depth = queueDepth();
  while (depth > 0 && Date.now() - startedAt < QUEUE_DRAIN_TIMEOUT_SECONDS * 1000) {
    sleep(1);
    depth = queueDepth();
  }
  const elapsedSeconds = ((Date.now() - startedAt) / 1000).toFixed(1);
  console.log(`queue drain: remaining=${depth} waited=${elapsedSeconds}s`);
  check(null, {[`큐 소진 (${QUEUE_DRAIN_TIMEOUT_SECONDS}초 이내)`]: () => depth === 0});
}

export function teardown(sessions) {
  waitForQueueDrain();

  const currentPrices = AUCTION_IDS.map((auctionId) => fetchCurrentPrice(auctionId, 'verify'));
  const points = sessions.map(fetchPoint);

  AUCTION_IDS.forEach((auctionId, index) => console.log(`auction=${auctionId} currentPrice=${currentPrices[index]}`));
  points.forEach((p) => console.log(`${p.email} available=${p.available} locked=${p.locked}`));

  if (MODE === 'smoke') {
    verifySmoke(currentPrices[0], points);
  } else if (MODE === 'concurrent' || MODE === 'rps' || MODE === 'mixed') {
    verifyConcurrent(currentPrices[0], points);
  } else {
    verifySpread(currentPrices, points);
  }
}

// 순차 실행이라 마지막 입찰가와 마지막 입찰자를 미리 계산할 수 있다
function verifySmoke(currentPrice, points) {
  const expectedPrice = START_PRICE + BID_STEP * SMOKE_ITERATIONS;
  const topBidderIndex = smokeBidderIndex(SMOKE_ITERATIONS - 1);

  check(null, {
    [`경매 현재가 = 마지막 입찰가(${expectedPrice})`]: () => currentPrice === expectedPrice,
  });

  // 마지막 입찰자만 마지막 입찰가만큼 잠겨 있고, 나머지는 전액 해제되어 있어야 한다
  points.forEach((p, index) => {
    const expectedLocked = index === topBidderIndex ? expectedPrice : 0;
    const expectedAvailable = INITIAL_POINT - expectedLocked;
    check(null, {
      [`${p.email} available=${expectedAvailable}, locked=${expectedLocked}`]:
        () => p.available === expectedAvailable && p.locked === expectedLocked,
    });
  });
}

// 동시 실행이라 누가 이길지 알 수 없으므로, 결과와 상관없이 항상 성립해야 하는 관계로 검증한다
function verifyConcurrent(currentPrice, points) {
  const lockedSum = points.reduce((sum, p) => sum + p.locked, 0);
  const holders = points.filter((p) => p.locked > 0);

  check(null, {
    '경매 현재가 > 시작가': () => currentPrice > START_PRICE,
    '입찰자 locked 합 = 경매 현재가': () => lockedSum === currentPrice,
    '포인트가 잠긴 입찰자는 1명이고 locked = 경매 현재가': () => holders.length === 1 && holders[0].locked === currentPrice,
  });

  checkPointBalance(points);
}

// 경매별 최고 입찰자는 API로 알 수 없으므로, 사람별 locked = 본인이 최고 입찰자인 경매들의 현재가 합은 verify_spread_test.sql에서 확인한다
// 여기서는 API로 확인할 수 있는 전체 합만 검증한다
function verifySpread(currentPrices, points) {
  const lockedSum = points.reduce((sum, p) => sum + p.locked, 0);
  const currentPriceSum = currentPrices.reduce((sum, price) => sum + price, 0);

  check(null, {
    '모든 경매 현재가 > 시작가': () => currentPrices.every((price) => price > START_PRICE),
    '입찰자 locked 합 = 경매 현재가 합': () => lockedSum === currentPriceSum,
  });

  checkPointBalance(points);
}

function checkPointBalance(points) {
  points.forEach((p) => {
    check(null, {
      [`${p.email} available + locked = ${INITIAL_POINT}`]: () => p.available + p.locked === INITIAL_POINT,
    });
  });
}
