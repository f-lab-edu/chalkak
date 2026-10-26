package com.chalkak.bid.event;

import com.chalkak.auction.controller.response.AuctionStatusResponse;
import java.util.UUID;

public record BidAcceptedEvent(
    UUID requestId,
    Long auctionId,
    Long bidderId,
    AuctionStatusResponse status
) {
}
