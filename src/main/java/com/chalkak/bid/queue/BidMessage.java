package com.chalkak.bid.queue;

import com.chalkak.bid.controller.request.BidRequest;
import java.util.UUID;

public record BidMessage(
    UUID requestId,
    Long auctionId,
    Long bidderId,
    BidRequest bidRequest
) {
}
