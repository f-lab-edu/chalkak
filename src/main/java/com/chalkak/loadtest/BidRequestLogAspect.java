package com.chalkak.loadtest;

import com.chalkak.auction.entity.Auction;
import com.chalkak.bid.controller.request.BidRequest;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
class BidRequestLogAspect {

    private final AtomicLong lockSeq = new AtomicLong();

    @Before("execution(* com.chalkak.bid.service.BidService.submit(..)) && args(.., request)")
    public void recordRequestPrice(BidRequest request) {
        BidRequestLog bidRequestLog = BidRequestLog.current();
        if (bidRequestLog != null) {
            bidRequestLog.requestPrice = request.bidAmount();
        }
    }

    @Around("execution(* com.chalkak.auction.repository.AuctionRepository.findByIdWithLock(..))")
    public Object recordLockAcquire(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        BidRequestLog bidRequestLog = BidRequestLog.current();
        if (bidRequestLog != null) {
            bidRequestLog.lockSeq = lockSeq.incrementAndGet();
            bidRequestLog.lockAcquireTime = System.currentTimeMillis();
            // 이 입찰이 반영되기 전의 현재가
            ((Optional<?>) result).ifPresent(auction -> bidRequestLog.currentPrice = ((Auction) auction).getCurrentPrice());
        }

        // 큐 방식에서는 락을 컨슈머 스레드에서 얻으므로, 같은 순번 체계로 컨슈머 측정 객체에 기록한다
        BidConsumeLog consumeLog = BidConsumeLog.current();
        if (consumeLog != null) {
            consumeLog.lockSeq = lockSeq.incrementAndGet();
            consumeLog.lockAcquireTime = System.currentTimeMillis();
            ((Optional<?>) result).ifPresent(auction -> consumeLog.currentPrice = ((Auction) auction).getCurrentPrice());
        }
        return result;
    }

    // SQS 발행이 끝난 시각. 접수 응답 시간 중 발행이 차지하는 몫을 본다
    @AfterReturning("execution(* com.chalkak.bid.queue.BidMessageProducer.send(..))")
    public void recordPublish() {
        BidRequestLog bidRequestLog = BidRequestLog.current();
        if (bidRequestLog != null) {
            bidRequestLog.publishTime = System.currentTimeMillis();
        }
    }

    // 요청 로그와 컨슈머 로그를 잇는 키
    @AfterReturning(pointcut = "execution(* com.chalkak.bid.service.BidRequestService.request(..))", returning = "requestId")
    public void recordRequestId(UUID requestId) {
        BidRequestLog bidRequestLog = BidRequestLog.current();
        if (bidRequestLog != null) {
            bidRequestLog.requestId = requestId;
        }
    }
}
