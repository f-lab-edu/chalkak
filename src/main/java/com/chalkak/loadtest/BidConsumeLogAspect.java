package com.chalkak.loadtest;

import com.chalkak.bid.queue.BidMessage;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.messaging.Message;

// 컨슈머 스레드에서 입찰 메시지 하나를 처리하는 동안의 측정값을 모아, 처리가 끝나면 로그 한 줄로 남긴다.
// 요청 스레드에서 BidRequestLogFilter가 하던 일을 컨슈머 스레드에서 한다.
@Slf4j
@Aspect
class BidConsumeLogAspect {

    @Around("execution(* com.chalkak.bid.queue.BidMessageConsumer.consume(..)) && args(message)")
    public Object recordConsume(ProceedingJoinPoint joinPoint, Message<BidMessage> message) throws Throwable {
        BidMessage bid = message.getPayload();
        String sqsSeq = message.getHeaders().get(SqsHeaders.MessageSystemAttributes.SQS_SEQUENCE_NUMBER, String.class);
        BidConsumeLog consumeLog = BidConsumeLog.start(
            bid.requestId(), message.getHeaders().getId(), bid.auctionId(), sqsSeq,
            System.currentTimeMillis(), bid.bidAmount());

        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            // BusinessException은 컨슈머 안에서 잡히므로, 여기까지 오는 예외는 메시지가 삭제되지 않고 재전달된다
            consumeLog.result = "ERROR";
            throw e;
        } finally {
            consumeLog.consumeEndTime = System.currentTimeMillis();
            log.info(consumeLog.format());
            BidConsumeLog.clear();
        }
    }

    @AfterReturning("execution(* com.chalkak.bid.service.BidRequestService.accept(..))")
    public void recordAccepted() {
        BidConsumeLog consumeLog = BidConsumeLog.current();
        if (consumeLog != null) {
            consumeLog.result = "ACCEPTED";
        }
    }

    @AfterReturning("execution(* com.chalkak.bid.service.BidRequestService.reject(..))")
    public void recordRejected() {
        BidConsumeLog consumeLog = BidConsumeLog.current();
        if (consumeLog != null) {
            consumeLog.result = "REJECTED";
        }
    }
}
