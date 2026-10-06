-- 입찰 부하 테스트 전 초기화 스크립트
-- hot 경매(22046)와 테스트 계정(test001~test100, user_id 2~101)의 포인트를 처음 상태로 되돌린다
-- 실행: docker exec -i chalkak-postgres psql -U chalkak -d chalkak -v ON_ERROR_STOP=1 < monitoring/k6/seed/reset_bid_test.sql

BEGIN;

DELETE FROM bids WHERE auction_id = 22046;

UPDATE auctions
SET current_price      = start_price,
    extended_closes_at = closes_at,
    status             = 'IN_PROGRESS',
    updated_at         = now()
WHERE id = 22046;

UPDATE points
SET available_amount = 50000000,
    locked_amount    = 0,
    updated_at       = now()
WHERE user_id BETWEEN 2 AND 101;

COMMIT;
