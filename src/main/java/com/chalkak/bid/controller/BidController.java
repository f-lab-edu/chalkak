package com.chalkak.bid.controller;

import com.chalkak.auth.principal.AuthUserPrincipal;
import com.chalkak.bid.controller.request.BidRequest;
import com.chalkak.bid.controller.response.BidAcceptedResponse;
import com.chalkak.bid.service.BidRequestService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auctions/{auctionId}/bids")
@RequiredArgsConstructor
public class BidController {

    private final BidRequestService bidRequestService;

    @PostMapping
    public ResponseEntity<BidAcceptedResponse> submit(
        @AuthenticationPrincipal AuthUserPrincipal principal,
        @PathVariable Long auctionId,
        @RequestHeader("Idempotency-Key") UUID idempotencyKey,
        @Valid @RequestBody BidRequest request
    ) {
        UUID requestId = bidRequestService.request(auctionId, principal.getUserId(), idempotencyKey, request);
        return ResponseEntity.accepted().body(new BidAcceptedResponse(requestId));
    }
}
