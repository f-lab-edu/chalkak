package com.chalkak.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.entity.AuctionStatus;
import com.chalkak.auction.entity.Camera;
import com.chalkak.auction.fixture.AuctionFixture;
import com.chalkak.auction.fixture.CameraFixture;
import com.chalkak.auction.repository.AuctionRepository;
import com.chalkak.auction.repository.CameraRepository;
import com.chalkak.common.util.TimeUtils;
import com.chalkak.user.entity.User;
import com.chalkak.user.fixture.UserFixture;
import com.chalkak.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class AuctionCloseConcurrencyTest {

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CameraRepository cameraRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        auctionRepository.deleteAll();
        cameraRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 입찰_처리가_경매_락을_잡고_있으면_마감은_기다렸다가_반영된_현재가로_판단한다() throws Exception {
        Long auctionId = createExpiredAuction().getId();
        CountDownLatch bidLocked = new CountDownLatch(1);
        CountDownLatch releaseBid = new CountDownLatch(1);

        CompletableFuture<Void> bid = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(status -> {
            Auction auction = auctionRepository.findByIdWithLock(auctionId).orElseThrow();
            auction.updateCurrentPrice(BigDecimal.valueOf(15_000));
            bidLocked.countDown();
            await(releaseBid);
        }));
        await(bidLocked);

        CompletableFuture<Void> close = CompletableFuture.runAsync(() -> auctionService.closeIfExpired(auctionId));
        Thread.sleep(300);
        assertThat(close).isNotDone();

        releaseBid.countDown();
        bid.get(5, TimeUnit.SECONDS);
        close.get(5, TimeUnit.SECONDS);

        Auction closed = auctionRepository.findById(auctionId).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(AuctionStatus.SUCCESSFUL);
        assertThat(closed.getCurrentPrice()).isEqualByComparingTo("15000");
    }

    private Auction createExpiredAuction() {
        User owner = userRepository.save(UserFixture.create());
        Camera camera = cameraRepository.save(CameraFixture.create(owner));
        Auction auction = AuctionFixture.create(camera, BigDecimal.valueOf(10_000), TimeUtils.now().plusDays(3));
        ReflectionTestUtils.setField(auction, "extendedClosesAt", TimeUtils.now().minusMinutes(1));
        return auctionRepository.save(auction);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
