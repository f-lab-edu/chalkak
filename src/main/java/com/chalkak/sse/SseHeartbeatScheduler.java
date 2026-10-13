package com.chalkak.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SseHeartbeatScheduler {

    private final SseEmitterStore sseEmitterStore;

    @Scheduled(fixedRate = 30_000)
    public void sendHeartbeat() {
        sseEmitterStore.sendHeartbeat();
    }
}
