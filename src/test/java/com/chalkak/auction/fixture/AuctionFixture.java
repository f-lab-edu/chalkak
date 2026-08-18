package com.chalkak.auction.fixture;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.entity.Camera;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AuctionFixture {

    public static final BigDecimal DEFAULT_START_PRICE = BigDecimal.valueOf(10_000);

    public static Auction create() {
        return create(CameraFixture.create());
    }

    public static Auction create(Camera camera) {
        return create(camera, DEFAULT_START_PRICE, LocalDateTime.now().plusDays(3));
    }

    public static Auction create(Camera camera, BigDecimal startPrice, LocalDateTime closesAt) {
        return Auction.start(camera, startPrice, closesAt);
    }
}
