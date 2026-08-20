package com.chalkak.point.controller;

import com.chalkak.auth.principal.AuthUserPrincipal;
import com.chalkak.point.controller.request.PointRequest;
import com.chalkak.point.controller.response.PointResponse;
import com.chalkak.point.service.PointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @GetMapping
    public ResponseEntity<PointResponse> getMyPoint(@AuthenticationPrincipal AuthUserPrincipal principal) {
        PointResponse response = pointService.findByUserId(principal.getUserId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/charge")
    public ResponseEntity<PointResponse> charge(
        @AuthenticationPrincipal AuthUserPrincipal principal,
        @Valid @RequestBody PointRequest request
    ) {
        PointResponse response = pointService.charge(principal.getUserId(), request.amount());
        return ResponseEntity.ok(response);
    }
}
