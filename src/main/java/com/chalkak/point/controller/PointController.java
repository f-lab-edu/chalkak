package com.chalkak.point.controller;

import com.chalkak.point.controller.request.PointRequest;
import com.chalkak.point.controller.response.PointResponse;
import com.chalkak.point.service.PointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    // TODO: Security 머지 후 @AuthenticationPrincipal AuthUserPrincipal에서 userId를 꺼내는 방식으로 교체
    @PostMapping("/charge")
    public ResponseEntity<PointResponse> charge(
        @RequestParam Long userId,
        @Valid @RequestBody PointRequest request
    ) {
        PointResponse response = pointService.charge(userId, request.amount());
        return ResponseEntity.ok(response);
    }
}
