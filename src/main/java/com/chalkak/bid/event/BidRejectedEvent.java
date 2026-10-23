package com.chalkak.bid.event;

import java.util.UUID;

public record BidRejectedEvent(
    UUID requestId,
    Long auctionId,
    Long bidderId,
    String errorCode
) {
}
