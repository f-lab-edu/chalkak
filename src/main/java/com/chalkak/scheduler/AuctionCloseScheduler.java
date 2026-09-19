package com.chalkak.scheduler;

import com.chalkak.auction.service.AuctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionCloseScheduler {

    private final AuctionService auctionService;

    @Scheduled(fixedDelayString = "${scheduler.auction-close.fixed-delay}")
    public void closeExpiredAuctions() {
        auctionService.closeExpiredAuctions();
    }
}
