package com.chalkak.bid.queue;

import com.chalkak.bid.service.BidRequestService;
import com.chalkak.common.exception.BusinessException;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BidMessageConsumer {

    private final BidRequestService bidRequestService;

    @SqsListener("${sqs.queue.bid}")
    public void consume(Message<BidMessage> message) {
        BidMessage bid = message.getPayload();
        if (bidRequestService.exists(bid.requestId())) {
            return;
        }

        try {
            bidRequestService.accept(bid);
        } catch (BusinessException e) {
            log.warn("BusinessException occurred: requestId={}, code={}, message={}", bid.requestId(), e.getErrorCode().getCode(), e.getMessage());
            bidRequestService.reject(bid, e);
        }
    }
}
