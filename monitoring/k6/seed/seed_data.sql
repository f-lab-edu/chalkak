-- k6 성능 테스트용 대량 더미 데이터 시드 스크립트
-- ⚠️ 전제: users/cameras/camera_images/auctions/bids 테이블이 비어있어야 함 (IDENTITY가 1부터 시작)
-- 기존 데이터를 지우고 이 스크립트 기준으로 다시 세팅하려면 TRUNCATE 구문 포함됨

BEGIN;

TRUNCATE bids, camera_images, auctions, cameras, points, users RESTART IDENTITY CASCADE;

-- 0. 관리자 계정 (로그인 확인용)
INSERT INTO users (email, password, phone, nickname, created_at, updated_at)
VALUES (
    'admin@admin.com',
    '$2b$10$qkfsh59QdSP0//5GOd.gv.ZA/LzlunPAPuYWhJ.Lp4.zgItD1HCwO', -- admin123!
    '010-0000-0000',
    'admin',
    now(),
    now()
);

-- 1. users (10만)
INSERT INTO users (email, password, phone, nickname, created_at, updated_at)
SELECT
    'user' || i || '@test.com',
    '$2a$10$loadtestdummyhashvalue000000000000000000',
    '010-' || lpad((i / 10000)::text, 4, '0') || '-' || lpad((i % 10000)::text, 4, '0'),
    'u' || i,
    now() - (random() * interval '365 days'),
    now()
FROM generate_series(1, 100000) AS i;

-- 2. cameras (30만), owner는 위 users 중 랜덤 (admin 제외하고 뽑히도록 id 2 ~ 100001 범위 사용)
INSERT INTO cameras (owner_id, category, brand, model_name, condition_grade, description, created_at, updated_at)
SELECT
    (floor(random() * 100000) + 2)::bigint,
    (ARRAY['DSLR','MIRRORLESS','FILM','LENS'])[floor(random() * 4) + 1],
    (ARRAY['Canon','Nikon','Sony','Fujifilm','Leica'])[floor(random() * 5) + 1],
    'Model-' || i,
    (ARRAY['S','A','B','C'])[floor(random() * 4) + 1],
    'Load test camera #' || i,
    now() - (random() * interval '365 days'),
    now()
FROM generate_series(1, 300000) AS i;

-- 3. camera_images (90만 = camera당 정확히 3장, 1~3.jpg 순환)
INSERT INTO camera_images (camera_id, image_key, created_at)
SELECT
    c.id,
    (1 + ((c.id * 3 + gs - 1) % 3))::text || '.jpg',
    now()
FROM cameras c
CROSS JOIN generate_series(1, 3) AS gs;

-- 4. auctions (30만, camera 1:1 / IN_PROGRESS 80% + 마감 20%)
WITH camera_pick AS (
    SELECT
        c.id AS camera_id,
        (10000 + floor(random() * 990000))::numeric AS start_price,
        CASE WHEN random() < 0.8 THEN 'IN_PROGRESS'
             ELSE (ARRAY['SUCCESSFUL','FAILED','CANCELLED'])[floor(random() * 3) + 1]
        END AS status
    FROM cameras c
),
with_time AS (
    SELECT *,
        CASE WHEN status = 'IN_PROGRESS' THEN now() + (random() * interval '30 days')
             ELSE now() - (random() * interval '30 days')
        END AS closes_at
    FROM camera_pick
)
INSERT INTO auctions (camera_id, start_price, current_price, status, closes_at, extended_closes_at, created_at, updated_at)
SELECT camera_id, start_price, start_price, status, closes_at, closes_at,
       now() - (random() * interval '365 days'), now()
FROM with_time;

-- 5. bids (140만, 멱법칙 분포로 소수 auction_id에 집중)
-- 주의: 랜덤 auction_id는 반드시 gs와 함께 서브쿼리 SELECT 절에서 먼저 계산해야 함.
-- JOIN 조건에 random()을 직접 쓰면 planner가 auctions를 기준으로 순회하면서
-- 조건을 auctions 행마다 한 번씩만 평가해버려 결과가 완전히 틀어짐 (실제로 겪은 버그).
INSERT INTO bids (auction_id, bidder_id, bid_amount, created_at, updated_at)
SELECT
    au.id,
    t.bidder_id,
    au.start_price + t.bid_extra,
    t.created_at,
    now()
FROM (
    SELECT
        (floor(power(random(), 3) * 300000) + 1)::bigint AS auction_id,
        (floor(random() * 100000) + 2)::bigint AS bidder_id,
        (1000 + floor(random() * 500000))::numeric AS bid_extra,
        now() - (random() * interval '30 days') AS created_at
    FROM generate_series(1, 1400000) AS gs
) t
JOIN auctions au ON au.id = t.auction_id;

-- 6. auctions.current_price를 실제 최고 입찰가로 정합성 맞추기
UPDATE auctions a
SET current_price = sub.max_bid
FROM (
    SELECT auction_id, MAX(bid_amount) AS max_bid
    FROM bids
    GROUP BY auction_id
) sub
WHERE a.id = sub.auction_id;

COMMIT;
