package com.chalkak.bid.controller;

import com.chalkak.auth.principal.AuthUserPrincipal;
import com.chalkak.bid.controller.response.BidResultResponse;
import com.chalkak.bid.service.BidRequestService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bid-requests")
@RequiredArgsConstructor
public class BidRequestController {

    private final BidRequestService bidRequestService;

    @GetMapping("/{requestId}")
    public ResponseEntity<BidResultResponse> getResult(
        @AuthenticationPrincipal AuthUserPrincipal principal,
        @PathVariable UUID requestId
    ) {
        BidResultResponse response = bidRequestService.getResult(requestId, principal.getUserId());
        return ResponseEntity.ok(response);
    }
}
