package com.chalkak.bid.queue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record BidMessage(
    UUID requestId,
    Long auctionId,
    Long bidderId,
    BigDecimal bidAmount,
    LocalDateTime requestedAt
) {
}
