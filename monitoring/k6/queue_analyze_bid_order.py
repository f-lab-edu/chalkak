"""큐 방식 입찰 순서 측정 로그(BID_REQUEST + BID_CONSUME) 정리 스크립트

k6를 돌린 뒤 실행하면 chalkak-application 컨테이너 로그에서 가장 최근 실행분을 골라,
요청 로그(BID_REQUEST)와 컨슈머 로그(BID_CONSUME)를 request_id로 이어서 파일을 만든다.
  1. bid_queue_<실행 시작 시각>.log          : 추출한 원본 로그
  2. bid_queue_<실행 시작 시각>_sorted.log   : 서버 도착 순서(request_seq)로 정렬하고 시각을 KST로 바꾼 표
  3. bid_queue_<실행 시작 시각>_sorted.html  : 2번과 같은 표에 역전 행을 색으로 표시 (브라우저나 Finder 미리보기로 연다)

순서는 세 지점에서 기록된다.
  request_seq : 필터에 도착한 순서
  sqs_seq     : SQS FIFO가 접수하며 부여한 SequenceNumber (같은 경매 = 같은 메시지 그룹 안에서 커진다)
  lock_seq    : 컨슈머가 경매 행 락을 얻은 순서

  ① 큐 이후 역전 (보장 대상, 0이어야 한다)
     경매별로 sqs_seq 순으로 정렬했을 때, 앞서 접수된 메시지보다 lock_seq가 작은 메시지.
     0이면 "같은 경매의 입찰은 SQS에 접수된 순서대로 처리한다"가 지켜진 것이다.
  ② 큐 이전 역전 (허용, 개수만 기록)
     경매별로 request_seq 순으로 정렬했을 때, 앞서 도착한 요청보다 sqs_seq가 작은 요청.
     필터 도착부터 SQS 접수까지 사이에 순서가 바뀐 것이다.
  같은 금액 역전
     같은 금액끼리 경쟁해서 순서가 앞선 요청이 거절되고 뒤인 요청이 성공한 경우. 거절된 요청 수로 센다.
     ① 큐 이후 (보장 대상, 0이어야 한다) : sqs_seq 기준. 먼저 접수됐는데 거절된 요청
     ② 큐 이전 (허용, 개수만 기록)       : request_seq 기준. 먼저 도착했는데 거절된 요청. ①이 0이면 ②의 큐 이전 역전 때문에 생긴 것이다.

실행:
  python3 monitoring/k6/queue_analyze_bid_order.py

실행 사이에 10초 이상 입찰 요청이 없으면 다른 실행으로 구분하므로, k6를 연달아 돌릴 때는 10초 이상 간격을 둔다.
202로 접수된 요청 수가 k6의 bid_accepted와 같은지, 처리 결과가 없는 요청이 0건인지 확인한다.
"""

import os
import subprocess
import sys
from collections import defaultdict
from datetime import datetime, timedelta, timezone

CONTAINER = 'chalkak-application'
# 실행 사이에는 입찰 요청이 없는 시간이 있으므로, 이보다 긴 공백이 있으면 다른 실행으로 본다
RUN_GAP_MS = 10_000
KST = timezone(timedelta(hours=9))
OUTPUT_DIR = os.path.dirname(os.path.abspath(__file__))
PROCESSED = ('ACCEPTED', 'REJECTED')


def fields_of(line, marker):
    raw = line[line.find(marker):].strip()
    return raw, dict(token.split('=', 1) for token in raw.split()[1:])


def parse_request(line):
    raw, fields = fields_of(line, 'BID_REQUEST')
    return {
        'raw': raw,
        'auction_id': int(fields['auction_id']),
        'request_seq': int(fields['request_seq']),
        # 202 전에 실패한 요청(400, 403)은 request_id가 없다
        'request_id': none_if_dash(fields.get('request_id', '-')),
        'request_time': int(fields['request_time']),
        'status': int(fields['status']),
    }


def parse_consume(line):
    raw, fields = fields_of(line, 'BID_CONSUME')
    return {
        'raw': raw,
        'auction_id': int(fields['auction_id']),
        'request_id': fields['request_id'],
        # SequenceNumber는 long 범위를 넘을 수 있지만 파이썬 int는 크기 제한이 없다
        'sqs_seq': int(fields['sqs_seq']),
        'lock_seq': to_int(fields['lock_seq']),
        'consume_time': int(fields['consume_time']),
        'lock_acquire_time': to_int(fields['lock_acquire_time']),
        'request_price': to_int(fields['request_price']),
        'current_price': to_int(fields['current_price']),
        'result': fields['result'],
    }


def none_if_dash(value):
    return None if value == '-' else value


def to_int(value):
    return None if value == '-' else int(value)


def kst(epoch_ms):
    return '-' if epoch_ms is None else datetime.fromtimestamp(epoch_ms / 1000, KST).strftime('%H:%M:%S.%f')[:-3]


def dash(value):
    return '-' if value is None else value


def extract_latest_run():
    """컨테이너 로그에서 가장 최근 실행분의 BID_REQUEST와, 그 요청들의 BID_CONSUME을 고른다"""
    result = subprocess.run(['docker', 'logs', CONTAINER], capture_output=True, text=True, check=True)
    lines = (result.stdout + result.stderr).splitlines()
    requests = [parse_request(line) for line in lines if 'BID_REQUEST' in line]
    if not requests:
        sys.exit(f"{CONTAINER} 로그에 BID_REQUEST가 없습니다.")

    # 같은 ms에 도착한 요청끼리는 request_seq 순서가 뒤바뀔 수 있으므로 실행 경계는 시간 공백으로만 판단한다
    requests.sort(key=lambda r: r['request_time'])
    start = 0
    for i in range(1, len(requests)):
        if requests[i]['request_time'] - requests[i - 1]['request_time'] > RUN_GAP_MS:
            start = i
    requests = requests[start:]

    # 컨슈머 처리는 요청보다 늦게 끝나서 시간으로 자를 수 없으므로, 이번 실행 요청의 request_id로 고른다
    request_ids = {r['request_id'] for r in requests if r['request_id']}
    consumes = [parse_consume(line) for line in lines if 'BID_CONSUME' in line]
    consumes = [c for c in consumes if c['request_id'] in request_ids]
    return requests, consumes


def join(requests, consumes):
    """요청마다 처리 결과를 붙인다. 같은 메시지가 다시 전달되면 BID_CONSUME이 여러 줄이므로 실제로 처리한 줄을 쓴다"""
    by_request_id = defaultdict(list)
    for consume in consumes:
        by_request_id[consume['request_id']].append(consume)

    for request in requests:
        lines = by_request_id.get(request['request_id'], [])
        processed = [c for c in lines if c['result'] in PROCESSED]
        request['consume'] = processed[0] if processed else (lines[0] if lines else None)
        request['delivery_count'] = len(lines)
    return by_request_id


def mark_after_queue_reversal(consumes):
    """① 경매별로 sqs_seq 순으로 훑으면서, 앞서 접수된 메시지 중 가장 큰 lock_seq보다 작은 lock_seq를 받은 메시지를 표시한다.
    락을 얻기 전에 실패했거나 건너뛴 메시지는 lock_seq가 없으므로 비교에서 뺀다."""
    max_lock_seq = defaultdict(int)
    count = 0
    for consume in sorted(consumes, key=lambda c: (c['auction_id'], c['sqs_seq'])):
        lock_seq = consume['lock_seq']
        consume['after_queue_reversal'] = lock_seq is not None and lock_seq < max_lock_seq[consume['auction_id']]
        if consume['after_queue_reversal']:
            count += 1
        if lock_seq is not None:
            max_lock_seq[consume['auction_id']] = max(max_lock_seq[consume['auction_id']], lock_seq)
    return count


def mark_before_queue_reversal(requests):
    """② request_seq 순으로 훑으면서, 앞서 도착한 요청 중 가장 큰 sqs_seq보다 작은 sqs_seq를 받은 요청을 표시한다.
    처리 결과가 없는 요청은 sqs_seq를 모르므로 비교에서 뺀다."""
    max_sqs_seq = defaultdict(int)
    count = 0
    for request in requests:
        consume = request['consume']
        sqs_seq = consume['sqs_seq'] if consume else None
        request['before_queue_reversal'] = sqs_seq is not None and sqs_seq < max_sqs_seq[request['auction_id']]
        if request['before_queue_reversal']:
            count += 1
        if sqs_seq is not None:
            max_sqs_seq[request['auction_id']] = max(max_sqs_seq[request['auction_id']], sqs_seq)
    return count


def mark_same_price_reversal(requests, order, flag):
    """같은 금액끼리 경쟁해서, order 기준으로 앞선 요청은 거절되고({flag}_loser) 뒤인 요청이 성공한({flag}_winner) 경우를 표시하고
    거절된 요청 수를 반환한다"""
    winners = {}
    for request in requests:
        request[f'{flag}_loser'] = request[f'{flag}_winner'] = False
        consume = request['consume']
        if consume and consume['result'] == 'ACCEPTED':
            winners[(request['auction_id'], consume['request_price'])] = request

    count = 0
    for request in requests:
        consume = request['consume']
        if not consume or consume['result'] != 'REJECTED':
            continue
        winner = winners.get((request['auction_id'], consume['request_price']))
        rejected_by_same_price = consume['current_price'] is not None and consume['current_price'] >= consume['request_price']
        if winner and rejected_by_same_price and order(request) < order(winner):
            request[f'{flag}_loser'] = True
            winner[f'{flag}_winner'] = True
            count += 1
    return count


def sqs_ranks(consumes):
    """SequenceNumber는 20자리라 표에서 읽기 어려우므로, 경매별 접수 순위(1부터)로 바꿔 보여준다"""
    ranks = {}
    by_auction = defaultdict(set)
    for consume in consumes:
        by_auction[consume['auction_id']].add(consume['sqs_seq'])
    for auction_id, seqs in by_auction.items():
        for rank, seq in enumerate(sorted(seqs), start=1):
            ranks[(auction_id, seq)] = rank
    return ranks


def row(request, ranks):
    c = request['consume']
    sqs_rank = ranks[(request['auction_id'], c['sqs_seq'])] if c else None
    queue_wait = c['consume_time'] - request['request_time'] if c else None
    return {
        'request_seq': request['request_seq'],
        'sqs_rank': dash(sqs_rank),
        'lock_seq': dash(c['lock_seq'] if c else None),
        'request_time': kst(request['request_time']),
        'consume_time': kst(c['consume_time'] if c else None),
        'queue_wait': dash(queue_wait),
        'request_price': dash(c['request_price'] if c else None),
        'current_price': dash(c['current_price'] if c else None),
        'status': request['status'],
        'result': c['result'] if c else '-',
    }


def note(request):
    notes = []
    consume = request['consume']
    if request['status'] == 202 and not consume:
        notes.append('처리 결과 없음')
    if consume and consume['after_queue_reversal']:
        notes.append('① 큐 이후 역전: 먼저 접수된 메시지보다 락을 먼저 얻음')
    if request['before_queue_reversal']:
        notes.append('② 큐 이전 역전: 먼저 도착한 요청보다 큐에 먼저 접수')
    if request['after_same_price_loser']:
        notes.append('① 같은 금액 역전: 먼저 접수됐는데 거절')
    if request['after_same_price_winner']:
        notes.append('① 같은 금액 역전: 늦게 접수됐는데 성공')
    if request['before_same_price_loser']:
        notes.append('② 같은 금액 역전: 먼저 도착했는데 거절')
    if request['before_same_price_winner']:
        notes.append('② 같은 금액 역전: 늦게 도착했는데 성공')
    if request['delivery_count'] > 1:
        notes.append(f"{request['delivery_count']}회 전달")
    return '  '.join(notes)


COLUMNS = [('request_seq', 'request_seq', 11), ('sqs_rank', 'sqs순위', 7), ('lock_seq', 'lock_seq', 8),
           ('request_time', '도착(KST)', 12), ('consume_time', '처리시작(KST)', 12), ('queue_wait', '큐대기ms', 8),
           ('request_price', '입찰가', 8), ('current_price', '직전현재가', 10), ('status', '응답', 4), ('result', '결과', 8)]


def write_raw(path, requests, consumes):
    with open(path, 'w', encoding='utf-8') as f:
        f.writelines(r['raw'] + '\n' for r in requests)
        f.writelines(c['raw'] + '\n' for c in sorted(consumes, key=lambda c: c['consume_time']))


def write_sorted(path, requests, ranks):
    with open(path, 'w', encoding='utf-8') as f:
        f.write('  '.join(f"{title:>{width}}" for _, title, width in COLUMNS) + '  비고\n')
        for request in requests:
            values = row(request, ranks)
            f.write('  '.join(f"{values[key]!s:>{width}}" for key, _, width in COLUMNS) + f"  {note(request)}\n")


def write_html(path, requests, ranks, summary):
    rows = []
    for request in requests:
        consume = request['consume']
        # 보장이 깨진 ①을 가장 눈에 띄게, 그다음 결과가 바뀐 ② 같은 금액 역전, ② 순서로 칠한다
        css = ('after' if (consume and consume['after_queue_reversal']) or request['after_same_price_loser']
               or request['after_same_price_winner']
               else 'missing' if request['status'] == 202 and not consume
               else 'loser' if request['before_same_price_loser'] else 'winner' if request['before_same_price_winner']
               else 'before' if request['before_queue_reversal'] else '')
        values = row(request, ranks)
        cells = ''.join(f"<td>{values[key]}</td>" for key, _, _ in COLUMNS)
        rows.append(f"<tr class='{css}'>{cells}<td class='note'>{note(request)}</td></tr>")

    headers = ''.join(f"<th>{title}</th>" for _, title, _ in COLUMNS)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(f"""<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><title>{os.path.basename(path)}</title>
<style>
  body {{ font-family: Menlo, Consolas, monospace; font-size: 13px; margin: 16px; background: #fff; color: #222; }}
  .legend span {{ display: inline-block; padding: 2px 8px; margin-right: 8px; border-radius: 3px; }}
  table {{ border-collapse: collapse; }}
  th {{ position: sticky; top: 0; background: #333; color: #fff; padding: 4px 10px; }}
  td {{ padding: 2px 10px; text-align: right; border-bottom: 1px solid #eee; }}
  td.note {{ text-align: left; }}
  .after {{ background: #ef9a9a; }}
  .missing {{ background: #b0bec5; }}
  .before {{ background: #fff59d; }}
  .loser {{ background: #ffcdd2; }}
  .winner {{ background: #c8e6c9; }}
</style></head><body>
<p>{'<br>'.join(summary)}</p>
<p class="legend"><span class="after">① 큐 이후 역전, ① 같은 금액 역전 (0이어야 함)</span><span class="missing">처리 결과 없음</span>
<span class="before">② 큐 이전 역전</span><span class="loser">② 같은 금액 역전: 먼저 도착했는데 거절</span>
<span class="winner">② 같은 금액 역전: 늦게 도착했는데 성공</span></p>
<table><thead><tr>{headers}<th>비고</th></tr></thead><tbody>
{chr(10).join(rows)}
</tbody></table></body></html>
""")


def percent(count, total):
    return f"{count / total * 100:.1f}%" if total else '-'


if __name__ == '__main__':
    requests, consumes = extract_latest_run()
    requests.sort(key=lambda r: r['request_seq'])
    by_request_id = join(requests, consumes)

    after_reversal = mark_after_queue_reversal(consumes)
    before_reversal = mark_before_queue_reversal(requests)
    after_same_price = mark_same_price_reversal(requests, lambda r: r['consume']['sqs_seq'], 'after_same_price')
    before_same_price = mark_same_price_reversal(requests, lambda r: r['request_seq'], 'before_same_price')
    ranks = sqs_ranks(consumes)

    accepted_requests = [r for r in requests if r['status'] == 202]
    missing = sum(1 for r in accepted_requests if not r['consume'])
    results = defaultdict(int)
    for consume in consumes:
        results[consume['result']] += 1
    redelivered = sum(1 for lines in by_request_id.values() if len(lines) > 1)
    processed = [r for r in accepted_requests if r['consume'] and r['consume']['result'] in PROCESSED]
    statuses = defaultdict(int)
    for request in requests:
        statuses[request['status']] += 1

    summary = [
        f"요청 {len(requests):,}건 (응답 코드별 {dict(sorted(statuses.items()))}) · 202 접수 {len(accepted_requests):,}건",
        f"컨슈머 로그 {len(consumes):,}줄 (결과별 {dict(sorted(results.items()))}) · 두 번 이상 전달된 요청 {redelivered:,}건",
        f"처리 결과 없음 {missing:,}건 (0이어야 함)",
        f"① 큐 이후 역전 {after_reversal:,}건 (0이어야 함)",
        f"② 큐 이전 역전 {before_reversal:,}건 ({percent(before_reversal, len(processed))})",
        f"① 같은 금액 역전 {after_same_price:,}건 (0이어야 함, 먼저 접수됐는데 거절된 요청)",
        f"② 같은 금액 역전 {before_same_price:,}건 (먼저 도착했는데 거절된 요청)",
    ]

    started_at = datetime.fromtimestamp(min(r['request_time'] for r in requests) / 1000, KST).strftime('%Y%m%d_%H%M%S')
    raw_path = os.path.join(OUTPUT_DIR, f"bid_queue_{started_at}.log")
    sorted_path = os.path.join(OUTPUT_DIR, f"bid_queue_{started_at}_sorted.log")
    html_path = os.path.join(OUTPUT_DIR, f"bid_queue_{started_at}_sorted.html")
    write_raw(raw_path, requests, consumes)
    write_sorted(sorted_path, requests, ranks)
    write_html(html_path, requests, ranks, summary)

    print(f"추출            {raw_path}")
    print(f"정렬            {sorted_path}")
    print(f"정렬(색 표시)   {html_path}")
    for line in summary:
        print(line)
