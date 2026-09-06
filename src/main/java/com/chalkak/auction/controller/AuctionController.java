package com.chalkak.auction.controller;

import com.chalkak.auction.controller.request.AuctionRequest;
import com.chalkak.auction.controller.response.AuctionDetailResponse;
import com.chalkak.auction.controller.response.AuctionResponse;
import com.chalkak.auction.controller.response.AuctionStatusResponse;
import com.chalkak.auction.controller.response.AuctionSummaryResponse;
import com.chalkak.auction.entity.AuctionSortType;
import com.chalkak.auction.entity.CameraCategory;
import com.chalkak.auction.entity.CameraConditionGrade;
import com.chalkak.auction.service.AuctionService;
import com.chalkak.auth.principal.AuthUserPrincipal;
import com.chalkak.common.response.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AuctionResponse> register(
        @AuthenticationPrincipal AuthUserPrincipal principal,
        @Valid @RequestPart("request") AuctionRequest request,
        @RequestPart("images") List<MultipartFile> images
    ) {
        AuctionResponse response = auctionService.register(principal.getUserId(), request, images);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<AuctionSummaryResponse>> getAuctions(
        @RequestParam(required = false) CameraCategory category,
        @RequestParam(required = false) CameraConditionGrade grade,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) AuctionSortType sort,
        Pageable pageable
    ) {
        PageResponse<AuctionSummaryResponse> response = auctionService.getAuctionsBySearchCondition(category, grade, keyword, sort, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{auctionId}")
    public ResponseEntity<AuctionDetailResponse> getDetail(@PathVariable Long auctionId) {
        AuctionDetailResponse response = auctionService.getDetail(auctionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{auctionId}/status")
    public ResponseEntity<AuctionStatusResponse> getStatus(@PathVariable Long auctionId) {
        AuctionStatusResponse response = auctionService.getStatus(auctionId);
        return ResponseEntity.ok(response);
    }
}
