package com.chalkak.bid.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.entity.Camera;
import com.chalkak.auction.fixture.AuctionFixture;
import com.chalkak.auction.fixture.CameraFixture;
import com.chalkak.auction.repository.AuctionRepository;
import com.chalkak.auction.repository.CameraRepository;
import com.chalkak.bid.controller.request.BidRequest;
import com.chalkak.bid.repository.BidRepository;
import com.chalkak.common.util.TimeUtils;
import com.chalkak.point.repository.PointRepository;
import com.chalkak.point.service.PointService;
import com.chalkak.user.entity.User;
import com.chalkak.user.fixture.UserFixture;
import com.chalkak.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BidConcurrencyTest {

    private static final int BIDDER_COUNT = 10;
    private static final BigDecimal START_PRICE = BigDecimal.valueOf(1_000);
    private static final BigDecimal WINNING_AMOUNT = BigDecimal.valueOf(5_000);
    private static final BigDecimal CHARGE_AMOUNT = BigDecimal.valueOf(10_000);

    @Autowired
    private BidService bidService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CameraRepository cameraRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private PointService pointService;

    @AfterEach
    void cleanUp() {
        bidRepository.deleteAll();
        pointRepository.deleteAll();
        auctionRepository.deleteAll();
        cameraRepository.deleteAll();
        userRepository.deleteAll();
    }

    @RepeatedTest(10)
    void 동시에_여러_입찰이_들어와도_최종_현재가는_최고_입찰가여야_한다() throws InterruptedException {
        int seed = ThreadLocalRandom.current().nextInt(1_000, 9_999);

        User owner = userRepository.save(UserFixture.create("owner-" + seed + "@chalkak.com", "encoded-password", phone(seed, 0)));
        Camera camera = cameraRepository.save(CameraFixture.create(owner));
        Auction auction = auctionRepository.save(
            AuctionFixture.create(camera, START_PRICE, TimeUtils.now().plusDays(3)));

        List<BigDecimal> bidAmounts = new ArrayList<>(List.of(
            WINNING_AMOUNT,
            BigDecimal.valueOf(1_500), BigDecimal.valueOf(1_850), BigDecimal.valueOf(2_200),
            BigDecimal.valueOf(2_550), BigDecimal.valueOf(2_900), BigDecimal.valueOf(3_250),
            BigDecimal.valueOf(3_600), BigDecimal.valueOf(3_950), BigDecimal.valueOf(4_300)
        ));
        Collections.shuffle(bidAmounts);

        List<Long> bidderIds = new ArrayList<>();
        for (int i = 0; i < BIDDER_COUNT; i++) {
            User bidder = userRepository.save(UserFixture.create("bidder" + i + "-" + seed + "@chalkak.com", "encoded-password", phone(seed, i + 1)));
            pointService.charge(bidder.getId(), CHARGE_AMOUNT);
            bidderIds.add(bidder.getId());
        }

        ExecutorService executor = Executors.newFixedThreadPool(BIDDER_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(BIDDER_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(BIDDER_COUNT);

        for (int i = 0; i < BIDDER_COUNT; i++) {
            Long bidderId = bidderIds.get(i);
            BigDecimal bidAmount = bidAmounts.get(i);
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    bidService.submit(auction.getId(), bidderId, new BidRequest(bidAmount));
                } catch (Exception ignored) {
                    // 더 높은 입찰이 먼저 반영된 뒤 낮은 입찰이 도착해 거절되는 건 정상 케이스라 무시한다.
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Auction finalAuction = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(finalAuction.getCurrentPrice()).isEqualByComparingTo(WINNING_AMOUNT);

    }

    @RepeatedTest(20)
    void 서로_다른_경매의_최고입찰자가_교차로_재입찰하면_데드락_없이_처리된다() throws InterruptedException {
        int seed = ThreadLocalRandom.current().nextInt(1_000, 9_999);

        User ownerA = userRepository.save(UserFixture.create("ownerA-" + seed + "@chalkak.com", "encoded-password", phone(seed, 0)));
        User ownerB = userRepository.save(UserFixture.create("ownerB-" + seed + "@chalkak.com", "encoded-password", phone(seed, 1)));
        Auction auctionA = auctionRepository.save(
            AuctionFixture.create(cameraRepository.save(CameraFixture.create(ownerA)), START_PRICE, TimeUtils.now().plusDays(3)));
        Auction auctionB = auctionRepository.save(
            AuctionFixture.create(cameraRepository.save(CameraFixture.create(ownerB)), START_PRICE, TimeUtils.now().plusDays(3)));

        User u1 = userRepository.save(UserFixture.create("u1-" + seed + "@chalkak.com", "encoded-password", phone(seed, 2)));
        User u2 = userRepository.save(UserFixture.create("u2-" + seed + "@chalkak.com", "encoded-password", phone(seed, 3)));
        pointService.charge(u1.getId(), CHARGE_AMOUNT);
        pointService.charge(u2.getId(), CHARGE_AMOUNT);

        bidService.submit(auctionA.getId(), u1.getId(), new BidRequest(BigDecimal.valueOf(1_500))); // U1이 A의 최고입찰자
        bidService.submit(auctionB.getId(), u2.getId(), new BidRequest(BigDecimal.valueOf(1_500))); // U2가 B의 최고입찰자

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                bidService.submit(auctionA.getId(), u2.getId(), new BidRequest(BigDecimal.valueOf(2_000))); // U2가 A에 재입찰
            } catch (Exception e) {
                exceptions.add(e);
            } finally {
                doneLatch.countDown();
            }
        });
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                bidService.submit(auctionB.getId(), u1.getId(), new BidRequest(BigDecimal.valueOf(2_000))); // U1이 B에 재입찰
            } catch (Exception e) {
                exceptions.add(e);
            } finally {
                doneLatch.countDown();
            }
        });

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(exceptions).isEmpty();
    }

    private String phone(int seed, int index) {
        return "010-%04d-%04d".formatted(seed, index);
    }
}
