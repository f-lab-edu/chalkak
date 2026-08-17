package com.chalkak.auction.controller.response;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.entity.AuctionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuctionResponse(
    Long id,
    Long cameraId,
    BigDecimal startPrice,
    BigDecimal currentPrice,
    AuctionStatus status,
    LocalDateTime closesAt
) {
    public static AuctionResponse from(Auction auction) {
        return new AuctionResponse(
            auction.getId(),
            auction.getCamera().getId(),
            auction.getStartPrice(),
            auction.getCurrentPrice(),
            auction.getStatus(),
            auction.getClosesAt()
        );
    }
}
