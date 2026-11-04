package com.chalkak.scheduler;

import com.chalkak.outbox.relay.OutboxRelay;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduler.outbox-relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {

    private final OutboxRelay outboxRelay;

    @Scheduled(fixedDelayString = "${scheduler.outbox-relay.fixed-delay}")
    public void relay() {
        outboxRelay.relay();
    }
}
