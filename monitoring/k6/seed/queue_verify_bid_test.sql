-- [큐(queue) 방식용] 입찰 부하 테스트 후 정합성 검증 스크립트
-- hot 경매(22046)의 입찰 기록·현재가와 테스트 계정(user_id 2~101)의 포인트가 서로 맞는지 확인하고,
-- 큐에 접수된 입찰이 빠짐없이 한 번씩 처리됐는지(유실, 중복) bid_requests로 확인한다
-- 컨슈머가 처리를 마친 뒤(k6 teardown의 큐 소진 대기가 끝난 뒤) 실행한다
-- 실행: docker exec -i chalkak-postgres psql -U chalkak -d chalkak < monitoring/k6/seed/queue_verify_bid_test.sql

\set auction_id 22046
\set initial_point 50000000

WITH auction AS (
    SELECT current_price, start_price
    FROM auctions
    WHERE id = :auction_id
),
bid_stats AS (
    SELECT count(*)                   AS bid_count,
           count(DISTINCT bid_amount) AS distinct_amount_count,
           max(bid_amount)            AS max_amount
    FROM bids
    WHERE auction_id = :auction_id
),
top_bid AS (
    SELECT bidder_id, bid_amount
    FROM bids
    WHERE auction_id = :auction_id
    ORDER BY bid_amount DESC
    LIMIT 1
),
-- 경매 행 락으로 직렬화됐다면 나중에 저장된 입찰(id가 큰 입찰)일수록 금액이 커야 한다
order_violation AS (
    SELECT count(*) AS violation_count
    FROM (SELECT bid_amount, lag(bid_amount) OVER (ORDER BY id) AS prev_amount
          FROM bids
          WHERE auction_id = :auction_id) t
    WHERE bid_amount <= prev_amount
),
lock_holder AS (
    SELECT user_id, locked_amount
    FROM points
    WHERE user_id BETWEEN 2 AND 101
      AND locked_amount > 0
),
request_stats AS (
    SELECT count(*)                                   AS request_count,
           count(*) FILTER (WHERE status = 'ACCEPTED') AS accepted_count,
           count(*) FILTER (WHERE status = 'REJECTED') AS rejected_count
    FROM bid_requests
    WHERE auction_id = :auction_id
),
-- 성공 결과가 가리키는 bids 행이 없거나, 같은 bids 행을 둘 이상의 결과가 가리키면 처리가 어긋난 것이다
unmatched_accepted AS (
    SELECT count(*) AS unmatched_count
    FROM bid_requests r
    LEFT JOIN bids b ON b.id = r.bid_id
    WHERE r.auction_id = :auction_id
      AND r.status = 'ACCEPTED'
      AND b.id IS NULL
),
unbalanced_point AS (
    SELECT count(*) AS unbalanced_count
    FROM points
    WHERE user_id BETWEEN 2 AND 101
      AND available_amount + locked_amount <> :initial_point
)
SELECT '1. 경매 현재가 = 최고 입찰가' AS check_name,
       coalesce(b.max_amount, a.start_price)::text AS expected,
       a.current_price::text AS actual,
       CASE WHEN a.current_price = coalesce(b.max_amount, a.start_price) THEN 'PASS' ELSE 'FAIL' END AS result
FROM auction a, bid_stats b

UNION ALL
SELECT '2. id 순서와 금액 순서가 어긋난 입찰 수',
       '0',
       v.violation_count::text,
       CASE WHEN v.violation_count = 0 THEN 'PASS' ELSE 'FAIL' END
FROM order_violation v

UNION ALL
SELECT '3. 같은 금액으로 성공한 입찰 수',
       '0',
       (b.bid_count - b.distinct_amount_count)::text,
       CASE WHEN b.bid_count = b.distinct_amount_count THEN 'PASS' ELSE 'FAIL' END
FROM bid_stats b

UNION ALL
SELECT '4. 포인트가 잠긴 계정 수',
       CASE WHEN b.bid_count > 0 THEN '1' ELSE '0' END,
       (SELECT count(*) FROM lock_holder)::text,
       CASE WHEN (SELECT count(*) FROM lock_holder) = CASE WHEN b.bid_count > 0 THEN 1 ELSE 0 END
            THEN 'PASS' ELSE 'FAIL' END
FROM bid_stats b

UNION ALL
SELECT '5. 최고 입찰자의 locked = 경매 현재가',
       'user_id=' || coalesce(t.bidder_id::text, '-') || ', locked=' || a.current_price,
       coalesce((SELECT string_agg('user_id=' || user_id || ', locked=' || locked_amount, ' / ') FROM lock_holder), '-'),
       CASE
           WHEN t.bidder_id IS NULL THEN CASE WHEN NOT EXISTS (SELECT 1 FROM lock_holder) THEN 'PASS' ELSE 'FAIL' END
           WHEN EXISTS (SELECT 1 FROM lock_holder h WHERE h.user_id = t.bidder_id AND h.locked_amount = a.current_price)
               THEN 'PASS'
           ELSE 'FAIL'
       END
FROM auction a
LEFT JOIN top_bid t ON true

UNION ALL
SELECT '6. available + locked <> 5천만 원인 계정 수',
       '0',
       u.unbalanced_count::text,
       CASE WHEN u.unbalanced_count = 0 THEN 'PASS' ELSE 'FAIL' END
FROM unbalanced_point u

UNION ALL
-- 같은 request_id는 한 번만 처리되므로(PK + 멱등성), 성공 결과 수와 bids 행 수가 다르면 중복 처리나 결과 누락이다
SELECT '7. 성공 결과(ACCEPTED) 수 = 입찰 기록 수',
       r.accepted_count::text,
       b.bid_count::text,
       CASE WHEN r.accepted_count = b.bid_count THEN 'PASS' ELSE 'FAIL' END
FROM request_stats r, bid_stats b

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
       r.request_count::text || ' (ACCEPTED ' || r.accepted_count || ', REJECTED ' || r.rejected_count || ')',
       'CHECK'
FROM request_stats r

UNION ALL
-- 정상 거절은 오류가 아니므로 사유별 건수만 보고한다
SELECT * FROM (
    SELECT '10. 거절 사유 ' || coalesce(error_code, '-'),
           '-',
           count(*)::text,
           'CHECK'
    FROM bid_requests
    WHERE auction_id = :auction_id
      AND status = 'REJECTED'
    GROUP BY error_code
    ORDER BY error_code
) rejected_reasons;
