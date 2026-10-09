package com.chalkak.loadtest;

import com.chalkak.auction.entity.Auction;
import com.chalkak.bid.controller.request.BidRequest;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.aspectj.lang.ProceedingJoinPoint;
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
        return result;
    }
}
