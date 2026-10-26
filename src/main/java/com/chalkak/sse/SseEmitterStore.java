package com.chalkak.sse;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

@Component
public class SseEmitterStore {
    private record Subscriber(Long userId, SseEmitter emitter) {
    }

    private static final long TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

    // 경매 ID : 경매 구독 연결 목록
    private final Map<Long, Set<Subscriber>> subscribers = new ConcurrentHashMap<>();

    // 비로그인 구독자는 userId가 null이고, 경매 전체에 보내는 이벤트만 받음(ex. 최고가)
    public SseEmitter subscribe(Long auctionId, Long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        Subscriber subscriber = new Subscriber(userId, emitter);
        subscribers.computeIfAbsent(auctionId, id -> ConcurrentHashMap.newKeySet()).add(subscriber);

        emitter.onCompletion(() -> remove(auctionId, subscriber));
        emitter.onTimeout(() -> remove(auctionId, subscriber));
        emitter.onError(e -> remove(auctionId, subscriber));
        return emitter;
    }

    public void broadcast(Long auctionId, String eventName, Object data) {
        Set<Subscriber> auctionSubscribers = subscribers.get(auctionId);
        if (auctionSubscribers == null) {
            return;
        }
        auctionSubscribers.forEach(subscriber -> send(auctionId, subscriber, SseEmitter.event().name(eventName).data(data)));
    }

    public void sendTo(Long auctionId, Long userId, String eventName, Object data) {
        Set<Subscriber> auctionSubscribers = subscribers.get(auctionId);
        if (auctionSubscribers == null) {
            return;
        }
        auctionSubscribers.stream()
            .filter(subscriber -> userId.equals(subscriber.userId()))
            .forEach(subscriber -> send(auctionId, subscriber, SseEmitter.event().name(eventName).data(data)));
    }

    public void sendHeartbeat() {
        subscribers.forEach((auctionId, subscribers) ->
            subscribers.forEach(subscriber -> send(auctionId, subscriber, SseEmitter.event().comment("heartbeat"))));
    }

    private void send(Long auctionId, Subscriber subscriber, SseEventBuilder event) {
        try {
            subscriber.emitter().send(event);
        } catch (IOException | IllegalStateException e) {
            remove(auctionId, subscriber);
            subscriber.emitter().completeWithError(e);
        }
    }

    private void remove(Long auctionId, Subscriber subscriber) {
        subscribers.computeIfPresent(auctionId, (id, subscribers) -> {
            subscribers.remove(subscriber);
            return subscribers.isEmpty() ? null : subscribers;
        });
    }

}
