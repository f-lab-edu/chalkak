package com.chalkak.sse;

import com.chalkak.auth.principal.AuthUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterStore sseEmitterStore;

    @GetMapping(value = "/auctions/{auctionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
        @AuthenticationPrincipal AuthUserPrincipal principal,
        @PathVariable Long auctionId
    ) {
        Long userId = principal == null ? null : principal.getUserId();
        return sseEmitterStore.subscribe(auctionId, userId);
    }
}
