package com.chalkak.bid.event;

import com.chalkak.bid.controller.response.BidResultResponse;
import com.chalkak.config.AsyncConfig;
import com.chalkak.sse.SseEmitterStore;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class BidResultSseListener {

    private static final String AUCTION_STATUS_EVENT = "auction-status";
    private static final String BID_RESULT_EVENT = "bid-result";

    private final SseEmitterStore sseEmitterStore;

    @Async(AsyncConfig.SSE_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccepted(BidAcceptedEvent event) {
        sseEmitterStore.broadcast(event.auctionId(), AUCTION_STATUS_EVENT, event.status());
        sseEmitterStore.sendTo(event.auctionId(), event.bidderId(), BID_RESULT_EVENT, BidResultResponse.accepted(event.requestId()));
    }

    @Async(AsyncConfig.SSE_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRejected(BidRejectedEvent event) {
        sseEmitterStore.sendTo(event.auctionId(), event.bidderId(), BID_RESULT_EVENT, BidResultResponse.rejected(event.requestId(), event.errorCode()));
    }
}
