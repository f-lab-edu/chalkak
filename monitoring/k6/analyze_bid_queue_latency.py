"""큐 방식 입찰 응답 시간 정리 스크립트

k6를 돌린 뒤 실행하면 chalkak-application 컨테이너 로그에서 가장 최근 실행분을 골라,
요청 로그(BID_REQUEST), 컨슈머 로그(BID_CONSUME), 메시지 삭제 로그(Done acknowledging)를 한 줄로 잇는다.
  BID_REQUEST --request_id--> BID_CONSUME --message_id--> Done acknowledging
결과는 bid_latency_<실행 시작 시각>.log에 서버 도착 순서(request_seq)로 남긴다.

시각
  ① 도착       request_time      필터에 도착
  ② 발행 완료  publish_time      SQS 발행이 끝남 (이후 202 응답)
  ④ 처리 시작  consume_time      컨슈머가 메시지를 받음
  ⑤ 락 획득    lock_acquire_time 경매 행 락을 얻음
  ⑥ 처리 완료  consume_end_time  커밋과 SSE 알림까지 끝남
  ⑦ 삭제 완료  ack 로그 시각     큐에서 메시지 삭제가 끝남

구간
  발행 ②-①, 큐 대기 ④-②, 처리 ⑥-④, 삭제 ⑦-⑥, 메시지 수명 ⑦-①
  사용자 응답 시간 ⑥-① : 사용자가 결과를 받기까지 걸린 시간. 동기 방식의 api:bid와 비교한다

실행:
  python3 monitoring/k6/analyze_bid_queue_latency.py

ack 로그는 application-loadtest.yaml에서 SqsAcknowledgementExecutor를 trace로 켜야 남는다.
"""

import os
import re
import subprocess
import sys
from collections import defaultdict
from datetime import datetime

from analyze_bid_queue_order import CONTAINER, KST, OUTPUT_DIR, PROCESSED, RUN_GAP_MS, fields_of, kst, none_if_dash, to_int

ACK_MARKER = 'Done acknowledging'
UUID_PATTERN = re.compile(r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}')
ACK_DURATION_PATTERN = re.compile(r' in (\d+)ms')

SPANS = [
    ('발행', 'publish_ms'),
    ('큐 대기', 'queue_wait_ms'),
    ('처리', 'process_ms'),
    ('삭제', 'delete_ms'),
    ('메시지 수명', 'lifetime_ms'),
    ('사용자 응답 시간', 'response_ms'),
]


def parse_request(line):
    _, fields = fields_of(line, 'BID_REQUEST')
    return {
        'request_seq': int(fields['request_seq']),
        # 202 전에 실패한 요청(400, 403)은 request_id가 없다
        'request_id': none_if_dash(fields['request_id']),
        'auction_id': int(fields['auction_id']),
        'request_time': int(fields['request_time']),
        'publish_time': to_int(fields['publish_time']),
        'status': int(fields['status']),
    }


def parse_consume(line):
    _, fields = fields_of(line, 'BID_CONSUME')
    return {
        'request_id': fields['request_id'],
        'message_id': fields['message_id'],
        'consume_time': int(fields['consume_time']),
        'lock_acquire_time': to_int(fields['lock_acquire_time']),
        'consume_end_time': int(fields['consume_end_time']),
        'result': fields['result'],
    }


def parse_ack(line):
    """'2026-10-17T10:32:04.151Z TRACE ... Done acknowledging in queue <큐> messages: [<id>, ...] in 9ms'"""
    logged_at = datetime.fromisoformat(line.split()[0].replace('Z', '+00:00'))
    ack_time = int(logged_at.timestamp() * 1000)
    message_ids = UUID_PATTERN.findall(line[line.find('messages:'):])
    duration = ACK_DURATION_PATTERN.search(line)
    return [(message_id, ack_time, int(duration.group(1))) for message_id in message_ids]


def extract_latest_run():
    """가장 최근 실행분의 BID_REQUEST와, 그 요청들의 BID_CONSUME, ack 로그를 고른다"""
    # 로그 줄 앞의 시각으로 ⑦을 구하므로 --timestamps 없이 Spring 로그 시각을 그대로 쓴다
    result = subprocess.run(['docker', 'logs', CONTAINER], capture_output=True, text=True, check=True)
    lines = (result.stdout + result.stderr).splitlines()
    requests = [parse_request(line) for line in lines if 'BID_REQUEST' in line]
    if not requests:
        sys.exit(f"{CONTAINER} 로그에 BID_REQUEST가 없습니다.")

    requests.sort(key=lambda r: r['request_time'])
    start = 0
    for i in range(1, len(requests)):
        if requests[i]['request_time'] - requests[i - 1]['request_time'] > RUN_GAP_MS:
            start = i
    requests = requests[start:]

    request_ids = {r['request_id'] for r in requests if r['request_id']}
    consumes = [parse_consume(line) for line in lines if 'BID_CONSUME' in line]
    consumes = [c for c in consumes if c['request_id'] in request_ids]

    message_ids = {c['message_id'] for c in consumes}
    acks = {}
    for line in lines:
        if ACK_MARKER in line:
            for message_id, ack_time, duration in parse_ack(line):
                if message_id in message_ids:
                    acks[message_id] = (ack_time, duration)
    return requests, consumes, acks


def join(requests, consumes, acks):
    """요청마다 처리 결과와 삭제 시각을 붙이고 구간 시간을 계산한다"""
    by_request_id = defaultdict(list)
    for consume in consumes:
        by_request_id[consume['request_id']].append(consume)

    for request in requests:
        lines = by_request_id.get(request['request_id'], [])
        # 같은 메시지가 다시 전달되면 BID_CONSUME이 여러 줄이므로 실제로 처리한 줄을 쓴다
        processed = [c for c in lines if c['result'] in PROCESSED]
        consume = processed[0] if processed else (lines[0] if lines else None)
        ack = acks.get(consume['message_id']) if consume else None

        request['consume'] = consume
        request['delivery_count'] = len(lines)
        request['ack_time'] = ack[0] if ack else None
        request['ack_duration'] = ack[1] if ack else None

        arrive = request['request_time']
        publish = request['publish_time']
        consume_start = consume['consume_time'] if consume else None
        consume_end = consume['consume_end_time'] if consume else None
        ack_time = request['ack_time']
        request['publish_ms'] = diff(publish, arrive)
        request['queue_wait_ms'] = diff(consume_start, publish)
        request['process_ms'] = diff(consume_end, consume_start)
        request['delete_ms'] = diff(ack_time, consume_end)
        request['lifetime_ms'] = diff(ack_time, arrive)
        request['response_ms'] = diff(consume_end, arrive)
    return by_request_id


def diff(later, earlier):
    return None if later is None or earlier is None else later - earlier


def dash(value):
    return '-' if value is None else value


HEADER = ['request_seq', 'request_id', 'message_id', 'auction_id', '결과',
          '①도착', '②발행완료', '④처리시작', '⑤락획득', '⑥처리완료', '⑦삭제완료',
          '발행ms', '큐대기ms', '처리ms', '삭제ms', '메시지수명ms', '사용자응답ms']


def row(request):
    consume = request['consume'] or {}
    return [
        request['request_seq'],
        dash(request['request_id']),
        consume.get('message_id', '-'),
        request['auction_id'],
        consume.get('result', f"status={request['status']}"),
        kst(request['request_time']),
        kst(request['publish_time']),
        kst(consume.get('consume_time')),
        kst(consume.get('lock_acquire_time')),
        kst(consume.get('consume_end_time')),
        kst(request['ack_time']),
        *[dash(request[key]) for _, key in SPANS],
    ]


def write_table(path, requests, summary):
    rows = [HEADER] + [[str(value) for value in row(r)] for r in requests]
    widths = [max(len(r[i]) for r in rows) for i in range(len(HEADER))]
    with open(path, 'w') as f:
        for line in summary:
            f.write(line + '\n')
        f.write('\n')
        for r in rows:
            f.write('  '.join(value.ljust(widths[i]) for i, value in enumerate(r)).rstrip() + '\n')


def percentile(sorted_values, p):
    # k6와 같은 방식(선형 보간)
    if not sorted_values:
        return None
    k = (len(sorted_values) - 1) * p / 100
    low = int(k)
    high = min(low + 1, len(sorted_values) - 1)
    return round(sorted_values[low] + (sorted_values[high] - sorted_values[low]) * (k - low), 1)


def span_summary(requests):
    lines = [f"{'구간':<12}{'건수':>8}{'p50':>10}{'p95':>10}{'p99':>10}{'max':>10}  (ms)"]
    for label, key in SPANS:
        values = sorted(r[key] for r in requests if r[key] is not None)
        stats = [percentile(values, p) for p in (50, 95, 99)] + [values[-1] if values else None]
        lines.append(f"{label:<12}{len(values):>8,}" + ''.join(f"{dash(v):>10}" for v in stats))
    return lines


if __name__ == '__main__':
    requests, consumes, acks = extract_latest_run()
    requests.sort(key=lambda r: r['request_seq'])
    by_request_id = join(requests, consumes, acks)

    accepted = [r for r in requests if r['status'] == 202]
    missing_consume = sum(1 for r in accepted if not r['consume'])
    missing_ack = sum(1 for r in accepted if r['consume'] and r['ack_time'] is None)
    redelivered = sum(1 for lines in by_request_id.values() if len(lines) > 1)
    results = defaultdict(int)
    for request in accepted:
        if request['consume']:
            results[request['consume']['result']] += 1

    summary = [
        f"요청 {len(requests):,}건 · 202 접수 {len(accepted):,}건 · 결과별 {dict(sorted(results.items()))}",
        f"처리 로그 없음 {missing_consume:,}건 · 삭제 로그 없음 {missing_ack:,}건 · 두 번 이상 전달 {redelivered:,}건 (모두 0이어야 함)",
        '',
        *span_summary(accepted),
    ]

    started_at = datetime.fromtimestamp(min(r['request_time'] for r in requests) / 1000, KST).strftime('%Y%m%d_%H%M%S')
    path = os.path.join(OUTPUT_DIR, f"bid_latency_{started_at}.log")
    write_table(path, requests, summary)

    print(f"결과  {path}")
    for line in summary:
        print(line)
