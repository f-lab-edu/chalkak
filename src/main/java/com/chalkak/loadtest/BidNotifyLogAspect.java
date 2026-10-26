package com.chalkak.loadtest;

import com.chalkak.bid.event.BidAcceptedEvent;
import com.chalkak.bid.event.BidRejectedEvent;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

// 입찰 결과 SSE 알림을 보낸 시각을 남긴다. request_id로 BID_REQUEST, BID_CONSUME과 이어 알림까지 걸린 시간을 구한다.
// @Async 프록시가 먼저 적용되므로 이 측정은 SSE 스레드 안에서 실행된다.
// 알림 시작 시각에서 BID_CONSUME의 consume_end_time을 빼면 SSE 스레드 대기열에서 기다린 시간이 된다.
@Slf4j
@Aspect
class BidNotifyLogAspect {

    @Around("execution(* com.chalkak.bid.event.BidResultSseListener.on*(..)) && args(event)")
    public Object recordNotify(ProceedingJoinPoint joinPoint, Object event) throws Throwable {
        long notifyStartTime = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } finally {
            long notifyEndTime = System.currentTimeMillis();
            log.info(format(event, notifyStartTime, notifyEndTime));
        }
    }

    private static String format(Object event, long notifyStartTime, long notifyEndTime) {
        String fields;
        if (event instanceof BidAcceptedEvent accepted) {
            fields = "request_id=" + accepted.requestId() + " auction_id=" + accepted.auctionId() + " result=ACCEPTED";
        } else if (event instanceof BidRejectedEvent rejected) {
            fields = "request_id=" + rejected.requestId() + " auction_id=" + rejected.auctionId() + " result=REJECTED";
        } else {
            fields = "request_id=- auction_id=- result=" + event.getClass().getSimpleName();
        }
        return "BID_NOTIFY " + fields
            + " notify_start_time=" + notifyStartTime
            + " notify_end_time=" + notifyEndTime;
    }
}
