package com.chalkak.bid.queue;

import com.chalkak.auction.controller.response.AuctionStatusResponse;
import com.chalkak.bid.controller.response.BidResultResponse;
import com.chalkak.bid.service.BidRequestService;
import com.chalkak.common.exception.BusinessException;
import com.chalkak.sse.SseEmitterStore;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BidMessageConsumer {

    private static final String AUCTION_STATUS_EVENT = "auction-status";
    private static final String BID_RESULT_EVENT = "bid-result";

    private final BidRequestService bidRequestService;
    private final SseEmitterStore sseEmitterStore;

    @SqsListener("${sqs.queue.bid}")
    public void consume(Message<BidMessage> message) {
        BidMessage bid = message.getPayload();
        if (bidRequestService.exists(bid.requestId())) {
            return;
        }

        try {
            AuctionStatusResponse status = bidRequestService.accept(bid);
            sseEmitterStore.broadcast(bid.auctionId(), AUCTION_STATUS_EVENT, status);
            sseEmitterStore.sendTo(bid.auctionId(), bid.bidderId(), BID_RESULT_EVENT, BidResultResponse.accepted(bid.requestId()));
        } catch (BusinessException e) {
            bidRequestService.reject(bid, e);
            sseEmitterStore.sendTo(bid.auctionId(), bid.bidderId(), BID_RESULT_EVENT, BidResultResponse.rejected(bid.requestId(), e.getErrorCode().getCode()));
        }
    }
}
