-- [동기(sync) 방식용] 입찰 부하 테스트 후 정합성 검증 스크립트
-- 대상 경매(hot 22046, spread 모드의 한산한 경매 175650, 192002, 203490, 220628)의 입찰 기록·현재가와
-- 테스트 계정(user_id 2~101)의 포인트가 서로 맞는지 확인한다
-- hot 모드에서는 한산한 경매에 입찰이 없으므로 현재가 = 시작가로 통과한다
-- 실행: docker exec -i chalkak-postgres psql -U chalkak -d chalkak < monitoring/k6/seed/sync_verify_bid_test.sql

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
-- 타임아웃 응답도 서버에서는 커밋됐을 수 있으므로 bid_accepted ≤ 입찰 기록 수 ≤ bid_accepted + bid_timeout 이면 정상
SELECT '7. 입찰 기록 수 (k6 결과와 직접 비교)',
       'bid_accepted ~ bid_accepted + bid_timeout',
       sum(b.bid_count)::text,
       'CHECK'
FROM bid_stats b

UNION ALL
SELECT * FROM (
    SELECT '7. [' || b.auction_id || '] 입찰 기록 수',
           '-',
           b.bid_count::text,
           'CHECK'
    FROM bid_stats b
    ORDER BY b.auction_id
) bid_by_auction;
