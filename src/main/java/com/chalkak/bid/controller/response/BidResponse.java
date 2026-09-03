package com.chalkak.bid.controller.response;

import com.chalkak.bid.entity.Bid;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BidResponse(
    Long id,
    Long auctionId,
    Long bidderId,
    BigDecimal bidAmount,
    LocalDateTime createdAt
) {
    public static BidResponse from(Bid bid) {
        return new BidResponse(
            bid.getId(),
            bid.getAuction().getId(),
            bid.getBidder().getId(),
            bid.getBidAmount(),
            bid.getCreatedAt()
        );
    }
}
