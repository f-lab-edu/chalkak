package com.chalkak.bid.queue;

import com.chalkak.bid.exception.BidErrorCode;
import com.chalkak.bid.service.BidRequestService;
import com.chalkak.common.exception.BusinessException;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

@Slf4j
@Component
@RequiredArgsConstructor
public class BidMessageConsumer {

    private final BidRequestService bidRequestService;
    private final BidMessageProducer bidMessageProducer;

    @Value("${sqs.queue.bid-max-receive-count}")
    private int maxReceiveCount;

    @SqsListener("${sqs.queue.bid}")
    public void consume(Message<BidMessage> message,
        @Header(SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT) int receiveCount) {
        BidMessage bid = message.getPayload();
        try {
            if (bidRequestService.exists(bid.requestId())) {
                return;
            }
            bidRequestService.accept(bid);
        } catch (BusinessException e) {
            log.warn("BusinessException occurred: requestId={}, code={}, message={}", bid.requestId(), e.getErrorCode().getCode(), e.getMessage());
            bidRequestService.reject(bid, e.getErrorCode());
        } catch (Exception e) {
            // 예외를 던지면 ack되지 않아 VisibilityTimeout 뒤에 다시 수신
            if (isRetryable(e) && receiveCount < maxReceiveCount) {
                log.warn("Bid processing failed, will retry: requestId={}, receiveCount={}", bid.requestId(), receiveCount, e);
                throw e;
            }
            log.error("Bid processing given up: requestId={}, receiveCount={}", bid.requestId(), receiveCount, e);
            bidRequestService.reject(bid, BidErrorCode.BID_NOT_PROCESSED);
            sendToDLQ(bid);
        }
    }

    // 일시적인 DB 문제만 재시도
    private boolean isRetryable(Exception e) {
        return e instanceof TransientDataAccessException
            || e instanceof CannotCreateTransactionException
            || e instanceof DataAccessResourceFailureException;
    }

    // 결과는 이미 저장됐으므로 DLQ 전송 실패는 기록만 남기고 ack한다
    private void sendToDLQ(BidMessage bid) {
        try {
            bidMessageProducer.sendToDLQ(bid);
        } catch (Exception e) {
            log.error("Failed to send bid to DLQ: requestId={}", bid.requestId(), e);
        }
    }
}
