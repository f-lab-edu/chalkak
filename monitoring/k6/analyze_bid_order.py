"""입찰 순서 측정 로그(BID_REQUEST) 정리 스크립트

k6를 돌린 뒤 실행하면 chalkak-application 컨테이너 로그에서 가장 최근 실행분의 BID_REQUEST만 골라 파일을 만든다.
  1. bid_request_<실행 시작 시각>.log          : 추출한 원본 로그
  2. bid_request_<실행 시작 시각>_sorted.log   : 서버 도착 순서(request_seq)로 정렬하고 시각을 KST로 바꾼 표
  3. bid_request_<실행 시작 시각>_sorted.html  : 2번과 같은 표에 추월·같은 금액 역전 행을 색으로 표시 (브라우저나 Finder 미리보기로 연다)

request_seq 순으로 정렬했을 때 lock_seq가 계속 커지면 도착 순서대로 락을 얻은 것이고,
앞서 도착한 요청보다 lock_seq가 작은 줄은 먼저 온 요청을 추월해 락을 얻은 것이다.

실행:
  python3 monitoring/k6/analyze_bid_order.py

실행 사이에 10초 이상 입찰 요청이 없으면 다른 실행으로 구분하므로, k6를 연달아 돌릴 때는 10초 이상 간격을 둔다.
추출한 줄 수가 k6의 bid_created + bid_rejected_low + bid_timeout + bid_unexpected와 같은지 확인한다.
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


def parse(line):
    raw = line[line.find('BID_REQUEST'):].strip()
    fields = dict(token.split('=', 1) for token in raw.split()[1:])
    return {
        'raw': raw,
        'auction_id': int(fields['auction_id']),
        'request_seq': int(fields['request_seq']),
        'lock_seq': to_int(fields['lock_seq']),
        'request_time': int(fields['request_time']),
        'lock_acquire_time': to_int(fields['lock_acquire_time']),
        'request_price': to_int(fields['request_price']),
        'current_price': to_int(fields['current_price']),
        'status': int(fields['status']),
    }


def to_int(value):
    return None if value == '-' else int(value)


def kst(epoch_ms):
    return '-' if epoch_ms is None else datetime.fromtimestamp(epoch_ms / 1000, KST).strftime('%H:%M:%S.%f')[:-3]


def dash(value):
    return '-' if value is None else value


def extract_latest_run():
    """컨테이너 로그에서 가장 최근 실행분의 BID_REQUEST만 고른다"""
    result = subprocess.run(['docker', 'logs', CONTAINER], capture_output=True, text=True, check=True)
    requests = [parse(line) for line in (result.stdout + result.stderr).splitlines() if 'BID_REQUEST' in line]
    if not requests:
        sys.exit(f"{CONTAINER} 로그에 BID_REQUEST가 없습니다.")

    # 같은 ms에 도착한 요청끼리는 request_seq 순서가 뒤바뀔 수 있으므로 실행 경계는 시간 공백으로만 판단한다
    requests.sort(key=lambda r: r['request_time'])
    start = 0
    for i in range(1, len(requests)):
        if requests[i]['request_time'] - requests[i - 1]['request_time'] > RUN_GAP_MS:
            start = i
    return requests[start:]


def mark_overtakers(requests):
    """도착 순서대로 훑으면서, 앞서 도착한 요청 중 가장 큰 lock_seq보다 작은 lock_seq를 받은 요청을 추월로 표시한다.
    락을 얻기 전에 실패한 요청은 lock_seq가 없으므로 비교에서 뺀다. 락 순서는 경매마다 따로 비교한다."""
    max_lock_seq = defaultdict(int)
    for request in requests:
        lock_seq = request['lock_seq']
        request['overtaker'] = lock_seq is not None and lock_seq < max_lock_seq[request['auction_id']]
        if lock_seq is not None:
            max_lock_seq[request['auction_id']] = max(max_lock_seq[request['auction_id']], lock_seq)


def mark_reversed(requests):
    """같은 금액끼리 경쟁해서, 먼저 도착한 요청은 거절되고(reversed_loser) 늦게 도착한 요청이 성공한(reversed_winner) 경우를 표시하고
    거절된 요청 수를 반환한다"""
    winners = {(r['auction_id'], r['request_price']): r for r in requests if r['status'] == 201}
    for request in requests:
        request['reversed_loser'] = request['reversed_winner'] = False

    count = 0
    for request in requests:
        winner = winners.get((request['auction_id'], request['request_price']))
        rejected_by_same_price = (request['status'] == 400 and request['current_price'] is not None
                                  and request['current_price'] >= request['request_price'])
        if winner and rejected_by_same_price and request['request_seq'] < winner['request_seq']:
            request['reversed_loser'] = True
            winner['reversed_winner'] = True
            count += 1
    return count


def note(request):
    notes = []
    if request['overtaker']:
        notes.append('← 추월')
    if request['reversed_loser']:
        notes.append('같은 금액 역전: 먼저 왔는데 거절')
    if request['reversed_winner']:
        notes.append('같은 금액 역전: 늦게 왔는데 성공')
    return '  '.join(notes)


def write_raw(path, requests):
    with open(path, 'w', encoding='utf-8') as f:
        f.writelines(r['raw'] + '\n' for r in requests)


def write_sorted(path, requests):
    with open(path, 'w', encoding='utf-8') as f:
        f.write(f"{'request_seq':>11}  {'lock_seq':>8}  {'도착(KST)':<12}  {'락획득(KST)':<12}  {'대기ms':>6}  "
                f"{'입찰가':>8}  {'현재가':>8}  {'상태':>4}  비고\n")
        for r in requests:
            wait = None if r['lock_acquire_time'] is None else r['lock_acquire_time'] - r['request_time']
            f.write(f"{r['request_seq']:>11}  {dash(r['lock_seq']):>8}  {kst(r['request_time']):<12}  "
                    f"{kst(r['lock_acquire_time']):<12}  {dash(wait):>6}  {dash(r['request_price']):>8}  "
                    f"{dash(r['current_price']):>8}  {r['status']:>4}  {note(r)}\n")


def write_html(path, requests, overtakers, reversed_count):
    rows = []
    for r in requests:
        wait = None if r['lock_acquire_time'] is None else r['lock_acquire_time'] - r['request_time']
        # 역전으로 결과가 바뀐 행을 추월보다 우선해서 칠한다
        css = ('loser' if r['reversed_loser'] else 'winner' if r['reversed_winner']
               else 'overtaker' if r['overtaker'] else '')
        cells = [r['request_seq'], dash(r['lock_seq']), kst(r['request_time']), kst(r['lock_acquire_time']),
                 dash(wait), dash(r['request_price']), dash(r['current_price']), r['status']]
        rows.append(f"<tr class='{css}'>" + ''.join(f"<td>{c}</td>" for c in cells) + f"<td class='note'>{note(r)}</td></tr>")

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
  .overtaker {{ background: #fff59d; }}
  .loser {{ background: #ffcdd2; }}
  .winner {{ background: #c8e6c9; }}
</style></head><body>
<p>요청 {len(requests):,}건 · 추월 {overtakers:,}건 ({overtakers / len(requests) * 100:.1f}%) · 같은 금액 역전 {reversed_count:,}건</p>
<p class="legend"><span class="overtaker">추월: 먼저 도착한 요청보다 락을 먼저 얻음</span>
<span class="loser">역전: 먼저 왔는데 거절</span><span class="winner">역전: 늦게 왔는데 성공</span></p>
<table><thead><tr><th>request_seq</th><th>lock_seq</th><th>도착(KST)</th><th>락획득(KST)</th><th>대기ms</th>
<th>입찰가</th><th>현재가</th><th>상태</th><th>비고</th></tr></thead><tbody>
{chr(10).join(rows)}
</tbody></table></body></html>
""")


if __name__ == '__main__':
    requests = sorted(extract_latest_run(), key=lambda r: r['request_seq'])
    mark_overtakers(requests)
    reversed_count = mark_reversed(requests)

    started_at = datetime.fromtimestamp(min(r['request_time'] for r in requests) / 1000, KST).strftime('%Y%m%d_%H%M%S')
    raw_path = os.path.join(OUTPUT_DIR, f"bid_request_{started_at}.log")
    sorted_path = os.path.join(OUTPUT_DIR, f"bid_request_{started_at}_sorted.log")
    html_path = os.path.join(OUTPUT_DIR, f"bid_request_{started_at}_sorted.html")
    overtakers = sum(r['overtaker'] for r in requests)
    write_raw(raw_path, requests)
    write_sorted(sorted_path, requests)
    write_html(html_path, requests, overtakers, reversed_count)

    print(f"추출            {len(requests):,}줄 → {raw_path}")
    print(f"정렬            {sorted_path}")
    print(f"정렬(색 표시)   {html_path}")
    print(f"추월            {overtakers:,}건 ({overtakers / len(requests) * 100:.1f}%)  먼저 도착한 요청보다 락을 먼저 얻은 요청")
    print(f"같은 금액 역전  {reversed_count:,}건  먼저 도착했는데 거절되고, 늦게 도착한 요청이 성공한 경우")
