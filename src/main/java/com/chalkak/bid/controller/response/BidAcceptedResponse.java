package com.chalkak.bid.controller.response;

import java.util.UUID;

public record BidAcceptedResponse(
    UUID requestId
) {
}
