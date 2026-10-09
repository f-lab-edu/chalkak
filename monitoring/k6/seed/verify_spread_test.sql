-- 다중 경매 입찰 부하 테스트 후 정합성 검증 스크립트
-- 다중 경매 5개의 입찰 기록·현재가와 테스트 계정(user_id 2~101)의 포인트가 서로 맞는지 확인한다
-- 실행: docker exec -i chalkak-postgres psql -U chalkak -d chalkak < monitoring/k6/seed/verify_spread_test.sql

\set auction_ids '175650, 192002, 203490, 220628, 299503'
\set initial_point 50000000

WITH auction AS (
    SELECT id, current_price, start_price
    FROM auctions
    WHERE id IN (:auction_ids)
),
bid_stats AS (
    SELECT a.id                         AS auction_id,
           count(b.id)                  AS bid_count,
           count(DISTINCT b.bid_amount) AS distinct_amount_count,
           max(b.bid_amount)            AS max_amount
    FROM auction a
    LEFT JOIN bids b ON b.auction_id = a.id
    GROUP BY a.id
),
-- 경매별 최고 입찰자 (입찰이 없는 경매는 빠진다)
top_bid AS (
    SELECT DISTINCT ON (auction_id) auction_id, bidder_id
    FROM bids
    WHERE auction_id IN (:auction_ids)
    ORDER BY auction_id, bid_amount DESC, id DESC
),
-- 경매 행 락으로 직렬화됐다면 같은 경매 안에서 나중에 저장된 입찰(id가 큰 입찰)일수록 금액이 커야 한다
order_violation AS (
    SELECT count(*) AS violation_count
    FROM (SELECT bid_amount, lag(bid_amount) OVER (PARTITION BY auction_id ORDER BY id) AS prev_amount
          FROM bids
          WHERE auction_id IN (:auction_ids)) t
    WHERE bid_amount <= prev_amount
),
-- 사람별 기대 locked = 본인이 최고 입찰자인 경매들의 현재가 합, 최고 입찰자인 경매가 없으면 0
point_expectation AS (
    SELECT p.user_id,
           coalesce(sum(a.current_price), 0) AS expected_locked,
           p.locked_amount                   AS actual_locked,
           p.available_amount
    FROM points p
    LEFT JOIN top_bid t ON t.bidder_id = p.user_id
    LEFT JOIN auction a ON a.id = t.auction_id
    WHERE p.user_id BETWEEN 2 AND 101
    GROUP BY p.user_id, p.locked_amount, p.available_amount
)
SELECT '1. 현재가 <> 최고 입찰가인 경매 수' AS check_name,
       '0' AS expected,
       count(*) FILTER (WHERE a.current_price <> coalesce(b.max_amount, a.start_price))::text AS actual,
       CASE WHEN count(*) FILTER (WHERE a.current_price <> coalesce(b.max_amount, a.start_price)) = 0
            THEN 'PASS' ELSE 'FAIL' END AS result
FROM auction a
JOIN bid_stats b ON b.auction_id = a.id

UNION ALL
-- 입찰이 없는 경매가 있으면 그 경매에서는 경합이 일어나지 않은 것이라 검증 범위가 줄어든다
SELECT '2. 입찰이 없는 경매 수',
       '0',
       count(*) FILTER (WHERE b.bid_count = 0)::text,
       CASE WHEN count(*) FILTER (WHERE b.bid_count = 0) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM bid_stats b

UNION ALL
SELECT '3. 경매별 id 순서와 금액 순서가 어긋난 입찰 수',
       '0',
       v.violation_count::text,
       CASE WHEN v.violation_count = 0 THEN 'PASS' ELSE 'FAIL' END
FROM order_violation v

UNION ALL
SELECT '4. 경매별 같은 금액으로 성공한 입찰 수',
       '0',
       sum(b.bid_count - b.distinct_amount_count)::text,
       CASE WHEN sum(b.bid_count - b.distinct_amount_count) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM bid_stats b

UNION ALL
SELECT '5. locked <> 본인이 최고 입찰자인 경매들의 현재가 합인 계정 수',
       '0',
       count(*) FILTER (WHERE e.actual_locked <> e.expected_locked)::text,
       CASE WHEN count(*) FILTER (WHERE e.actual_locked <> e.expected_locked) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM point_expectation e

UNION ALL
SELECT '6. 전체 locked 합 = 입찰이 있는 경매들의 현재가 합',
       (SELECT coalesce(sum(a.current_price), 0) FROM auction a JOIN top_bid t ON t.auction_id = a.id)::text,
       sum(e.actual_locked)::text,
       CASE WHEN sum(e.actual_locked) = (SELECT coalesce(sum(a.current_price), 0) FROM auction a JOIN top_bid t ON t.auction_id = a.id)
            THEN 'PASS' ELSE 'FAIL' END
FROM point_expectation e

UNION ALL
SELECT '7. available + locked <> 5천만 원인 계정 수',
       '0',
       count(*) FILTER (WHERE e.available_amount + e.actual_locked <> :initial_point)::text,
       CASE WHEN count(*) FILTER (WHERE e.available_amount + e.actual_locked <> :initial_point) = 0
            THEN 'PASS' ELSE 'FAIL' END
FROM point_expectation e

UNION ALL
-- 타임아웃 응답도 서버에서는 커밋됐을 수 있으므로 bid_created ≤ 입찰 기록 수 ≤ bid_created + bid_timeout 이면 정상
SELECT '8. 입찰 기록 수 (k6 결과와 직접 비교)',
       'bid_created ~ bid_created + bid_timeout',
       sum(b.bid_count)::text,
       'CHECK'
FROM bid_stats b;

-- 경매별 상세: 현재가, 최고 입찰가, 최고 입찰자
SELECT a.id                AS auction_id,
       a.current_price,
       b.max_amount        AS max_bid_amount,
       t.bidder_id         AS top_bidder_id,
       b.bid_count
FROM auctions a
JOIN (SELECT auction_id, count(*) AS bid_count, max(bid_amount) AS max_amount
      FROM bids
      WHERE auction_id IN (:auction_ids)
      GROUP BY auction_id) b ON b.auction_id = a.id
LEFT JOIN (SELECT DISTINCT ON (auction_id) auction_id, bidder_id
           FROM bids
           WHERE auction_id IN (:auction_ids)
           ORDER BY auction_id, bid_amount DESC, id DESC) t ON t.auction_id = a.id
ORDER BY a.id;

-- 사람별 상세: 포인트가 잠겨 있거나 최고 입찰자인 경매가 있는 계정만 출력
SELECT p.user_id,
       string_agg(t.auction_id::text, ', ' ORDER BY t.auction_id) AS top_bid_auctions,
       coalesce(sum(a.current_price), 0)                         AS expected_locked,
       p.locked_amount                                           AS actual_locked,
       p.available_amount
FROM points p
LEFT JOIN (SELECT DISTINCT ON (auction_id) auction_id, bidder_id
           FROM bids
           WHERE auction_id IN (:auction_ids)
           ORDER BY auction_id, bid_amount DESC, id DESC) t ON t.bidder_id = p.user_id
LEFT JOIN auctions a ON a.id = t.auction_id
WHERE p.user_id BETWEEN 2 AND 101
GROUP BY p.user_id, p.locked_amount, p.available_amount
HAVING p.locked_amount > 0 OR count(t.auction_id) > 0
ORDER BY p.user_id;
