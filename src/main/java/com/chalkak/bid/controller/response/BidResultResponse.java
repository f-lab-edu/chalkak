package com.chalkak.bid.controller.response;

import com.chalkak.bid.entity.BidRequestResult;
import java.util.UUID;

public record BidResultResponse(
    UUID requestId,
    BidResultStatus status,
    String errorCode
) {

    public static BidResultResponse accepted(UUID requestId) {
        return new BidResultResponse(requestId, BidResultStatus.ACCEPTED, null);
    }

    public static BidResultResponse rejected(UUID requestId, String errorCode) {
        return new BidResultResponse(requestId, BidResultStatus.REJECTED, errorCode);
    }

    public static BidResultResponse pending(UUID requestId) {
        return new BidResultResponse(requestId, BidResultStatus.PENDING, null);
    }

    public static BidResultResponse from(BidRequestResult result) {
        return new BidResultResponse(result.getRequestId(), BidResultStatus.from(result.getStatus()), result.getErrorCode());
    }
}
