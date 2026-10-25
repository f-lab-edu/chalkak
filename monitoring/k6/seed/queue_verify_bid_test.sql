-- [큐(queue) 방식용] 입찰 부하 테스트 후 정합성 검증 스크립트
-- 대상 경매(hot 22046, spread 모드의 한산한 경매 175650, 192002, 203490, 220628)의 입찰 기록·현재가와
-- 테스트 계정(user_id 2~101)의 포인트가 서로 맞는지 확인하고,
-- 큐에 접수된 입찰이 빠짐없이 한 번씩 처리됐는지(유실, 중복) bid_requests로 확인한다
-- hot 모드에서는 한산한 경매에 입찰이 없으므로 현재가 = 시작가로 통과한다
-- 컨슈머가 처리를 마친 뒤(k6 teardown의 큐 소진 대기가 끝난 뒤) 실행한다
-- 실행: docker exec -i chalkak-postgres psql -U chalkak -d chalkak < monitoring/k6/seed/queue_verify_bid_test.sql

\set initial_point 50000000

WITH target AS (
    SELECT id AS auction_id, current_price, start_price
    FROM auctions
    WHERE id IN (22046, 175650, 192002, 203490, 220628)
),
bid_stats AS (
    SELECT t.auction_id,
           count(b.id)                  AS bid_count,
           count(DISTINCT b.bid_amount) AS distinct_amount_count,
           max(b.bid_amount)            AS max_amount
    FROM target t
    LEFT JOIN bids b ON b.auction_id = t.auction_id
    GROUP BY t.auction_id
),
-- 경매 행 락으로 직렬화됐다면 같은 경매에서 나중에 저장된 입찰(id가 큰 입찰)일수록 금액이 커야 한다
order_violation AS (
    SELECT t.auction_id, count(o.auction_id) AS violation_count
    FROM target t
    LEFT JOIN (SELECT auction_id, bid_amount, lag(bid_amount) OVER (PARTITION BY auction_id ORDER BY id) AS prev_amount
               FROM bids
               WHERE auction_id IN (SELECT auction_id FROM target)) o
           ON o.auction_id = t.auction_id AND o.bid_amount <= o.prev_amount
    GROUP BY t.auction_id
),
-- 경매마다 최고 입찰자만 그 경매 현재가만큼 포인트가 잠겨 있어야 한다. 한 계정이 여러 경매의 최고 입찰자일 수 있다
top_bid AS (
    SELECT DISTINCT ON (auction_id) auction_id, bidder_id, bid_amount
    FROM bids
    WHERE auction_id IN (SELECT auction_id FROM target)
    ORDER BY auction_id, bid_amount DESC
),
expected_lock AS (
    SELECT bidder_id AS user_id, sum(bid_amount) AS locked_amount
    FROM top_bid
    GROUP BY bidder_id
),
lock_holder AS (
    SELECT user_id, locked_amount
    FROM points
    WHERE user_id BETWEEN 2 AND 101
      AND locked_amount > 0
),
lock_mismatch AS (
    SELECT count(*) AS mismatch_count
    FROM expected_lock e
    FULL OUTER JOIN lock_holder h ON h.user_id = e.user_id
    WHERE e.locked_amount IS DISTINCT FROM h.locked_amount
),
request_stats AS (
    SELECT t.auction_id,
           count(r.request_id)                           AS request_count,
           count(*) FILTER (WHERE r.status = 'ACCEPTED') AS accepted_count,
           count(*) FILTER (WHERE r.status = 'REJECTED') AS rejected_count
    FROM target t
    LEFT JOIN bid_requests r ON r.auction_id = t.auction_id
    GROUP BY t.auction_id
),
-- 성공 결과가 가리키는 bids 행이 없거나, 같은 bids 행을 둘 이상의 결과가 가리키면 처리가 어긋난 것이다
unmatched_accepted AS (
    SELECT count(*) AS unmatched_count
    FROM bid_requests r
    LEFT JOIN bids b ON b.id = r.bid_id
    WHERE r.auction_id IN (SELECT auction_id FROM target)
      AND r.status = 'ACCEPTED'
      AND b.id IS NULL
),
unbalanced_point AS (
    SELECT count(*) AS unbalanced_count
    FROM points
    WHERE user_id BETWEEN 2 AND 101
      AND available_amount + locked_amount <> :initial_point
)
SELECT * FROM (
    SELECT '1. [' || t.auction_id || '] 경매 현재가 = 최고 입찰가' AS check_name,
           coalesce(b.max_amount, t.start_price)::text AS expected,
           t.current_price::text AS actual,
           CASE WHEN t.current_price = coalesce(b.max_amount, t.start_price) THEN 'PASS' ELSE 'FAIL' END AS result
    FROM target t
    JOIN bid_stats b ON b.auction_id = t.auction_id
    ORDER BY t.auction_id
) current_price_check

UNION ALL
SELECT * FROM (
    SELECT '2. [' || v.auction_id || '] id 순서와 금액 순서가 어긋난 입찰 수',
           '0',
           v.violation_count::text,
           CASE WHEN v.violation_count = 0 THEN 'PASS' ELSE 'FAIL' END
    FROM order_violation v
    ORDER BY v.auction_id
) order_check

UNION ALL
SELECT * FROM (
    SELECT '3. [' || b.auction_id || '] 같은 금액으로 성공한 입찰 수',
           '0',
           (b.bid_count - b.distinct_amount_count)::text,
           CASE WHEN b.bid_count = b.distinct_amount_count THEN 'PASS' ELSE 'FAIL' END
    FROM bid_stats b
    ORDER BY b.auction_id
) same_amount_check

UNION ALL
SELECT '4. 포인트가 잠긴 계정 수 = 최고 입찰자 수',
       (SELECT count(*) FROM expected_lock)::text,
       (SELECT count(*) FROM lock_holder)::text,
       CASE WHEN (SELECT count(*) FROM expected_lock) = (SELECT count(*) FROM lock_holder) THEN 'PASS' ELSE 'FAIL' END

UNION ALL
SELECT '5. 계정별 locked = 최고 입찰한 경매 현재가 합이 아닌 계정 수',
       '0',
       m.mismatch_count::text,
       CASE WHEN m.mismatch_count = 0 THEN 'PASS' ELSE 'FAIL' END
FROM lock_mismatch m

UNION ALL
SELECT '6. available + locked <> 5천만 원인 계정 수',
       '0',
       u.unbalanced_count::text,
       CASE WHEN u.unbalanced_count = 0 THEN 'PASS' ELSE 'FAIL' END
FROM unbalanced_point u

UNION ALL
-- 같은 request_id는 한 번만 처리되므로(PK + 멱등성), 성공 결과 수와 bids 행 수가 다르면 중복 처리나 결과 누락이다
SELECT * FROM (
    SELECT '7. [' || r.auction_id || '] 성공 결과(ACCEPTED) 수 = 입찰 기록 수',
           r.accepted_count::text,
           b.bid_count::text,
           CASE WHEN r.accepted_count = b.bid_count THEN 'PASS' ELSE 'FAIL' END
    FROM request_stats r
    JOIN bid_stats b ON b.auction_id = r.auction_id
    ORDER BY r.auction_id
) accepted_check

UNION ALL
SELECT '8. bids 행이 없는 성공 결과 수',
       '0',
       u.unmatched_count::text,
       CASE WHEN u.unmatched_count = 0 THEN 'PASS' ELSE 'FAIL' END
FROM unmatched_accepted u

UNION ALL
-- 타임아웃 응답도 큐에는 접수됐을 수 있으므로 bid_accepted ≤ 처리 결과 수 ≤ bid_accepted + bid_timeout 이면 유실 없음
SELECT '9. 처리 결과 수 (ACCEPTED + REJECTED, k6 결과와 직접 비교)',
       'bid_accepted ~ bid_accepted + bid_timeout',
       sum(r.request_count)::text || ' (ACCEPTED ' || sum(r.accepted_count) || ', REJECTED ' || sum(r.rejected_count) || ')',
       'CHECK'
FROM request_stats r

UNION ALL
SELECT * FROM (
    SELECT '9. [' || r.auction_id || '] 처리 결과 수',
           '-',
           r.request_count::text || ' (ACCEPTED ' || r.accepted_count || ', REJECTED ' || r.rejected_count || ')',
           'CHECK'
    FROM request_stats r
    ORDER BY r.auction_id
) request_by_auction

UNION ALL
-- 정상 거절은 오류가 아니므로 사유별 건수만 보고한다
SELECT * FROM (
    SELECT '10. 거절 사유 ' || coalesce(error_code, '-'),
           '-',
           count(*)::text,
           'CHECK'
    FROM bid_requests
    WHERE auction_id IN (SELECT auction_id FROM target)
      AND status = 'REJECTED'
    GROUP BY error_code
    ORDER BY error_code
) rejected_reasons;
