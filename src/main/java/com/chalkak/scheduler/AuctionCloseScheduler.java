package com.chalkak.scheduler;

import com.chalkak.auction.service.AuctionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionCloseScheduler {

    private final AuctionService auctionService;

    @Scheduled(fixedDelayString = "${scheduler.auction-close.fixed-delay}")
    public void closeExpiredAuctions() {
        for (Long auctionId : auctionService.findExpiredAuctionIds()) {
            try {
                auctionService.closeIfExpired(auctionId);
            } catch (Exception e) {
                log.error("Auction close failed: auctionId={}", auctionId, e);
            }
        }
    }
}
