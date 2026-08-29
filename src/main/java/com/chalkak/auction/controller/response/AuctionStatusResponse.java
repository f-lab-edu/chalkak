package com.chalkak.auction.controller.response;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.entity.AuctionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuctionStatusResponse(
    BigDecimal currentPrice,
    AuctionStatus status,
    LocalDateTime closesAt
) {

    public static AuctionStatusResponse from(Auction auction) {
        return new AuctionStatusResponse(
            auction.getCurrentPrice(),
            auction.getStatus(),
            auction.getExtendedClosesAt()
        );
    }
}
