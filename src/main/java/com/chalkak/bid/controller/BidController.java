package com.chalkak.bid.controller;

import com.chalkak.auth.principal.AuthUserPrincipal;
import com.chalkak.bid.controller.request.BidRequest;
import com.chalkak.bid.controller.response.BidResponse;
import com.chalkak.bid.service.BidService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auctions/{auctionId}/bids")
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;

    @PostMapping
    public ResponseEntity<BidResponse> submit(
        @AuthenticationPrincipal AuthUserPrincipal principal,
        @PathVariable Long auctionId,
        @Valid @RequestBody BidRequest request
    ) {
        BidResponse response = bidService.submit(auctionId, principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
