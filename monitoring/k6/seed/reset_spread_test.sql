-- 다중 경매 입찰 부하 테스트 전 초기화 스크립트
-- 다중 경매 5개(175650, 192002, 203490, 220628, 299503)와 테스트 계정(test001~test100, user_id 2~101)의 포인트를 처음 상태로 되돌린다
-- 5개 경매는 hot 경매(22046)와 같은 조건(시작가 10,000원, 마감 2026-10-30 23:59:59)으로 맞춰둔 경매다
-- 실행: docker exec -i chalkak-postgres psql -U chalkak -d chalkak -v ON_ERROR_STOP=1 < monitoring/k6/seed/reset_spread_test.sql

BEGIN;

DELETE FROM bids WHERE auction_id IN (175650, 192002, 203490, 220628, 299503);

UPDATE auctions
SET current_price      = start_price,
    extended_closes_at = closes_at,
    status             = 'IN_PROGRESS',
    updated_at         = now()
WHERE id IN (175650, 192002, 203490, 220628, 299503);

UPDATE points
SET available_amount = 50000000,
    locked_amount    = 0,
    updated_at       = now()
WHERE user_id BETWEEN 2 AND 101;

COMMIT;
