package com.chalkak.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SseHeartbeatScheduler {

    private final SseEmitterStore sseEmitterStore;

    @Scheduled(fixedDelayString = "${sse.heartbeat-interval}")
    public void sendHeartbeat() {
        sseEmitterStore.sendHeartbeat();
    }
}
