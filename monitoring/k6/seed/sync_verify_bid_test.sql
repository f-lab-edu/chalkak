-- [동기(sync) 방식용] 입찰 부하 테스트 후 정합성 검증 스크립트
-- hot 경매(22046)의 입찰 기록·현재가와 테스트 계정(user_id 2~101)의 포인트가 서로 맞는지 확인한다
-- 실행: docker exec -i chalkak-postgres psql -U chalkak -d chalkak < monitoring/k6/seed/sync_verify_bid_test.sql

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
-- 타임아웃 응답도 서버에서는 커밋됐을 수 있으므로 bid_accepted ≤ 입찰 기록 수 ≤ bid_accepted + bid_timeout 이면 정상
SELECT '7. 입찰 기록 수 (k6 결과와 직접 비교)',
       'bid_accepted ~ bid_accepted + bid_timeout',
       b.bid_count::text,
       'CHECK'
FROM bid_stats b;
