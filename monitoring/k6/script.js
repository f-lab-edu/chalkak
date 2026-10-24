import http from 'k6/http';
import exec from 'k6/execution';
import {check, sleep} from 'k6';
import {Counter} from 'k6/metrics';

const BASE_URL = 'http://localhost:8080';

// 띄워 둔 서버의 입찰 처리 방식에 맞춘다. 방식에 따라 입찰 응답과 검증 전 대기가 달라진다
// sync: 요청 안에서 입찰을 처리하고 201(성공) 또는 400 AUCTION-004(현재가 이하)를 응답한다
// queue: 큐에 접수만 하고 202를 응답한다. 결과는 컨슈머가 나중에 처리하므로 bid_requests와 BID_CONSUME 로그로 확인한다
const BID_MODE = __ENV.BID_MODE || 'queue';
if (BID_MODE !== 'sync' && BID_MODE !== 'queue') {
  throw new Error(`알 수 없는 BID_MODE: ${BID_MODE} (sync | queue)`);
}
// 워밍업(JIT 등)용 실행: 리셋 없이 연달아 돌릴 수 있도록 리셋 확인과 정합성 검증을 건너뛴다. 본 측정 전에 리셋한다
const WARMUP = __ENV.WARMUP === 'true';
// k6는 host 네트워크로 실행되므로 LocalStack에 localhost로 접근한다
const SQS_ENDPOINT = 'http://localhost:4566';
const BID_QUEUE_URL = `${SQS_ENDPOINT}/000000000000/bid-queue.fifo`;
// queue: 검증 전에 큐가 빌 때까지 기다린다
const QUEUE_DRAIN_TIMEOUT_SECONDS = 300;

// 테스트 대상 경매: 입찰 기록 없음, 시작가 10,000원, 마감 2026-10-30 23:59:59
const HOT_AUCTION_ID = 22046;
const START_PRICE = 10000;
const BID_STEP = 100;

// test001 ~ test005 계정 사용 (비밀번호: test{번호}123!, 포인트 5천만 원씩)
const BIDDER_COUNT = 5;
const INITIAL_POINT = 50000000;

// 실행: k6 run -e BID_MODE=sync|queue -e RATE=200 -e PRE_VUS=50 -e MAX_VUS=2500 -e LIST_RATE=100 -e LIST_PRE_VUS=100 -e LIST_MAX_VUS=500 script.js
// 입찰 1번 = 현재가 조회 + 입찰 2요청이라 초당 HTTP 요청 수는 RATE의 2배
const BID_RATE = intEnv('RATE', 50);
const DURATION = '30s';
// 필요한 VU ≈ RATE × 반복 1번에 걸리는 시간(초). 서버가 느려지면 필요한 VU가 늘고, MAX_VUS를 넘으면 k6가 dropped_iterations로 기록한다
const BID_PRE_VUS = intEnv('PRE_VUS', 50);
// k6 컨테이너(메모리 1GB)는 VU 3000개 정도에서 메모리를 80% 쓰므로, 목록 조회 LIST_MAX_VUS(500)와 합쳐 3000 안에 들게 둔다
const BID_MAX_VUS = intEnv('MAX_VUS', 2500, BID_PRE_VUS);
// 입찰과 함께 초당 시작하는 경매 목록 조회 수
const LIST_RATE = intEnv('LIST_RATE', 100);
// 시나리오끼리 VU를 나눠 쓰지 않으므로 입찰의 MAX_VUS와 따로 둔다
const LIST_PRE_VUS = intEnv('LIST_PRE_VUS', 100);
const LIST_MAX_VUS = intEnv('LIST_MAX_VUS', 500, LIST_PRE_VUS);

function intEnv(name, defaultValue, min = 1) {
  const value = Number(__ENV[name] || defaultValue);
  if (!Number.isInteger(value) || value < min) {
    throw new Error(`${name}는 ${min} 이상의 정수여야 합니다: ${__ENV[name]}`);
  }
  return value;
}

// sync에서는 입찰 성공(201), queue에서는 큐 접수(202) 건수
const bidAccepted = new Counter('bid_accepted');
// sync 전용: 같은 현재가를 읽은 입찰끼리 경쟁해서 밀린 건수
const bidRejectedLow = new Counter('bid_rejected_low');
const bidTimeout = new Counter('bid_timeout');
const bidUnexpected = new Counter('bid_unexpected');

export const options = {
  // queue: 기본 60초로는 큐가 다 비기 전에 teardown이 끊길 수 있다
  teardownTimeout: BID_MODE === 'queue' ? `${QUEUE_DRAIN_TIMEOUT_SECONDS + 60}s` : '60s',
  // 입찰을 일정한 속도로 보내면서 경매 목록을 함께 조회해서, 입찰이 몰릴 때 다른 API가 얼마나 느려지는지 본다
  // 둘 다 응답을 기다리지 않고 시작하므로, 서버가 느려져도 요청 속도는 줄지 않고 응답 시간에 그대로 드러난다
  scenarios: {
    bid: {
      executor: 'constant-arrival-rate',
      exec: 'bidAboveCurrentPrice',
      rate: BID_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: BID_PRE_VUS,
      maxVUs: BID_MAX_VUS,
    },
    list: {
      executor: 'constant-arrival-rate',
      exec: 'listAuctions',
      rate: LIST_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: LIST_PRE_VUS,
      maxVUs: LIST_MAX_VUS,
    },
  },
  thresholds: {
    // 최대 부하까지 올려서 타임아웃이 나더라도 정합성이 지켜지는지 보는 것이 목적이라 타임아웃은 실패 조건에서 뺀다
    // 타임아웃 건은 서버에 반영됐을 수 있으므로 DB 입찰 기록(sync: bids, queue: bid_requests) 수와 대조한다
    bid_accepted: ['count>0'],
    bid_unexpected: ['count==0'],
    checks: ['rate==1'],
    // http_req_duration은 로그인·입찰·조회를 모두 합산하므로, 요청 종류별로 나눠 summary에 출력한다
    'http_req_duration{api:login}': [],
    'http_req_duration{api:read}': [],
    'http_req_duration{api:bid}': [],
    'http_req_duration{api:verify}': [],
    'http_req_duration{api:list}': [],
  },
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

function errorCode(res) {
  // 에러 응답이 JSON이 아니면 res.json()이 예외를 던지므로, 그 경우 코드 없음으로 처리한다
  try {
    return res.json('code');
  } catch (e) {
    return null;
  }
}

function bid(session, auctionId, amount) {
  const params = authParams(session);
  // 재시도하지 않으므로 입찰마다 새 키를 쓴다. 동기 서버는 이 헤더를 읽지 않는다
  params.headers['Idempotency-Key'] = crypto.randomUUID();
  const res = http.post(
    `${BASE_URL}/api/v1/auctions/${auctionId}/bids`,
    JSON.stringify({bidAmount: amount}),
    Object.assign(params, {timeout: '10s', tags: {api: 'bid'}}),
  );

  if (res.status === (BID_MODE === 'sync' ? 201 : 202)) {
    bidAccepted.add(1);
  } else if (BID_MODE === 'sync' && res.status === 400 && errorCode(res) === 'AUCTION-004') {
    bidRejectedLow.add(1);
  } else if (res.status === 0) {
    // 클라이언트 타임아웃 또는 연결 실패: 서버에는 반영됐을 수 있어서 금액을 남겨두고 DB와 대조한다
    bidTimeout.add(1);
    console.error(`TIMEOUT_BID auction=${auctionId} bidder=${session.email} amount=${amount} error=${res.error}`);
  } else {
    bidUnexpected.add(1);
    console.error(`UNEXPECTED_BID auction=${auctionId} bidder=${session.email} amount=${amount} status=${res.status} body=${res.body}`);
  }
}

export function setup() {
  // 직전 실행의 메시지가 남아 있으면 이번 실행 중에 함께 처리되어 결과가 섞이므로, 큐가 비어 있어야 시작한다
  if (BID_MODE === 'queue') {
    const depth = queueDepth();
    if (depth > 0) {
      exec.test.abort(`큐에 메시지 ${depth}건이 남아 있습니다. 큐를 비우고 reset_bid_test.sql을 다시 실행하세요.`);
    }
  }

  // 이전 테스트의 입찰이 남아 있으면 기대값과 불변식 검증이 모두 틀어지므로, 리셋 여부를 먼저 확인한다
  if (!WARMUP) {
    const currentPrice = fetchCurrentPrice(HOT_AUCTION_ID, 'verify');
    if (currentPrice !== START_PRICE) {
      exec.test.abort(`경매 ${HOT_AUCTION_ID} 현재가가 ${currentPrice}원입니다. reset_bid_test.sql을 먼저 실행하세요.`);
    }
  }

  // 카운터가 한 번도 기록되지 않으면 threshold 판정에서 빠질 수 있어서 0으로 먼저 기록해 둔다
  bidAccepted.add(0);
  bidRejectedLow.add(0);
  bidTimeout.add(0);
  bidUnexpected.add(0);

  const sessions = [];
  for (let i = 0; i < BIDDER_COUNT; i++) {
    sessions.push(login(i));
  }
  return sessions;
}

export function listAuctions() {
  const res = http.get(`${BASE_URL}/api/v1/auctions`, {tags: {api: 'list'}});
  check(res, {'경매 목록 조회 200': (r) => r.status === 200});
}

// 사용자처럼 현재가를 보고 한 단계 높여 입찰한다
// 여러 VU가 같은 현재가를 읽으므로 같은 금액끼리 경쟁하고, 먼저 처리된 한 건만 성공한다
export function bidAboveCurrentPrice(sessions) {
  const session = sessions[Math.floor(Math.random() * BIDDER_COUNT)];
  const currentPrice = fetchCurrentPrice(HOT_AUCTION_ID, 'read');
  if (currentPrice === null) {
    return;
  }
  bid(session, HOT_AUCTION_ID, currentPrice + BID_STEP);
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
  // 워밍업도 큐는 비워야 다음 실행이 시작 전 큐 확인을 통과한다
  if (BID_MODE === 'queue') {
    waitForQueueDrain();
  }
  if (WARMUP) {
    return;
  }

  const currentPrice = fetchCurrentPrice(HOT_AUCTION_ID, 'verify');
  const points = sessions.map(fetchPoint);

  console.log(`auction=${HOT_AUCTION_ID} currentPrice=${currentPrice}`);
  points.forEach((p) => console.log(`${p.email} available=${p.available} locked=${p.locked}`));

  verifyConsistency(currentPrice, points);
}

// 동시 실행이라 누가 이길지 알 수 없으므로, 결과와 상관없이 항상 성립해야 하는 관계로 검증한다
function verifyConsistency(currentPrice, points) {
  const lockedSum = points.reduce((sum, p) => sum + p.locked, 0);
  const holders = points.filter((p) => p.locked > 0);

  check(null, {
    '경매 현재가 > 시작가': () => currentPrice > START_PRICE,
    '입찰자 locked 합 = 경매 현재가': () => lockedSum === currentPrice,
    '포인트가 잠긴 입찰자는 1명이고 locked = 경매 현재가': () => holders.length === 1 && holders[0].locked === currentPrice,
  });

  points.forEach((p) => {
    check(null, {
      [`${p.email} available + locked = ${INITIAL_POINT}`]: () => p.available + p.locked === INITIAL_POINT,
    });
  });
}
