package com.chalkak.bid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.entity.Camera;
import com.chalkak.auction.exception.AuctionErrorCode;
import com.chalkak.auction.fixture.AuctionFixture;
import com.chalkak.auction.fixture.CameraFixture;
import com.chalkak.auction.repository.AuctionRepository;
import com.chalkak.auction.repository.CameraRepository;
import com.chalkak.bid.controller.request.BidRequest;
import com.chalkak.bid.controller.response.BidResponse;
import com.chalkak.bid.repository.BidRepository;
import com.chalkak.common.exception.BusinessException;
import com.chalkak.common.exception.CommonErrorCode;
import com.chalkak.common.util.TimeUtils;
import com.chalkak.point.controller.response.PointResponse;
import com.chalkak.point.exception.PointErrorCode;
import com.chalkak.point.service.PointService;
import com.chalkak.user.entity.User;
import com.chalkak.user.fixture.UserFixture;
import com.chalkak.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BidServiceTest {

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
    private PointService pointService;

    private Auction createAuction(LocalDateTime closesAt) {
        Camera camera = cameraRepository.save(
            CameraFixture.create(userRepository.save(UserFixture.create())));
        return auctionRepository.save(AuctionFixture.create(camera, BigDecimal.valueOf(1_000), closesAt));
    }

    private Auction createAuction() {
        return createAuction(TimeUtils.now().plusDays(3));
    }

    @Test
    void 최초_입찰이면_현재가가_갱신되고_입찰자_포인트가_잠긴다() {
        Auction auction = createAuction();

        User bidder = userRepository.save(UserFixture.create("bidder1@chalkak.com", "encoded-password", "010-1111-1111"));
        pointService.charge(bidder.getId(), BigDecimal.valueOf(50_000));

        BigDecimal bidAmount = BigDecimal.valueOf(2_000);

        BidResponse response = bidService.submit(auction.getId(), bidder.getId(), new BidRequest(bidAmount));

        assertThat(response.auctionId()).isEqualTo(auction.getId());
        assertThat(response.bidderId()).isEqualTo(bidder.getId());
        assertThat(response.bidAmount()).isEqualByComparingTo(bidAmount);
        assertThat(auction.getCurrentPrice()).isEqualByComparingTo(bidAmount);

        PointResponse bidderPoint = pointService.findByUserId(bidder.getId());
        assertThat(bidderPoint.availableAmount()).isEqualByComparingTo(BigDecimal.valueOf(50_000).subtract(bidAmount));
        assertThat(bidderPoint.lockedAmount()).isEqualByComparingTo(bidAmount);
    }

    @Test
    void 기존_최고_입찰자가_있을때_더_높은_입찰이_들어오면_이전_입찰자_포인트는_풀리고_새_입찰자_포인트가_잠긴다() {
        Auction auction = createAuction();

        User firstBidder = userRepository.save(UserFixture.create("bidder1@chalkak.com", "encoded-password", "010-1111-1111"));
        pointService.charge(firstBidder.getId(), BigDecimal.valueOf(50_000));
        BigDecimal firstBidAmount = BigDecimal.valueOf(2_000);
        bidService.submit(auction.getId(), firstBidder.getId(), new BidRequest(firstBidAmount));

        User secondBidder = userRepository.save(UserFixture.create("bidder2@chalkak.com", "encoded-password", "010-2222-2222"));
        pointService.charge(secondBidder.getId(), BigDecimal.valueOf(50_000));
        BigDecimal secondBidAmount = BigDecimal.valueOf(3_000);

        BidResponse response = bidService.submit(auction.getId(), secondBidder.getId(), new BidRequest(secondBidAmount));

        assertThat(response.bidAmount()).isEqualByComparingTo(secondBidAmount);
        assertThat(auction.getCurrentPrice()).isEqualByComparingTo(secondBidAmount);

        PointResponse firstBidderPoint = pointService.findByUserId(firstBidder.getId());
        assertThat(firstBidderPoint.availableAmount()).isEqualByComparingTo(BigDecimal.valueOf(50_000));
        assertThat(firstBidderPoint.lockedAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        PointResponse secondBidderPoint = pointService.findByUserId(secondBidder.getId());
        assertThat(secondBidderPoint.availableAmount()).isEqualByComparingTo(BigDecimal.valueOf(50_000).subtract(secondBidAmount));
        assertThat(secondBidderPoint.lockedAmount()).isEqualByComparingTo(secondBidAmount);

        assertThat(bidRepository.count()).isEqualTo(2);
    }

    @Test
    void 같은_입찰자가_최고_입찰_상태에서_재입찰하면_차액만_포인트가_잠긴다() {
        Auction auction = createAuction();

        User bidder = userRepository.save(UserFixture.create("bidder1@chalkak.com", "encoded-password", "010-1111-1111"));
        pointService.charge(bidder.getId(), BigDecimal.valueOf(2_500));

        BigDecimal firstBidAmount = BigDecimal.valueOf(2_000);
        bidService.submit(auction.getId(), bidder.getId(), new BidRequest(firstBidAmount));

        BigDecimal secondBidAmount = BigDecimal.valueOf(2_400);
        BidResponse response = bidService.submit(auction.getId(), bidder.getId(), new BidRequest(secondBidAmount));

        assertThat(response.bidAmount()).isEqualByComparingTo(secondBidAmount);
        assertThat(auction.getCurrentPrice()).isEqualByComparingTo(secondBidAmount);

        PointResponse bidderPoint = pointService.findByUserId(bidder.getId());
        assertThat(bidderPoint.availableAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(bidderPoint.lockedAmount()).isEqualByComparingTo(secondBidAmount);

        assertThat(bidRepository.count()).isEqualTo(2);
    }

    @Test
    void 존재하지_않는_경매면_예외가_발생한다() {
        User bidder = userRepository.save(UserFixture.create());
        BigDecimal bidAmount = BigDecimal.valueOf(10_000);
        Long bidderId = bidder.getId();
        BidRequest request = new BidRequest(bidAmount);

        assertThatThrownBy(() -> bidService.submit(-1L, bidderId, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.NOT_FOUND)
            .hasMessage("경매 정보가 존재하지 않습니다.");
    }

    // TODO: Auction.status가 CANCELLED/FAILED/SUCCESSFUL일 때도 AUCTION_ALREADY_CLOSED가 발생하는지 검증 필요.
    // Auction을 그 상태로 전환하는 도메인 메서드(cancel() 등)가 아직 없어서 정상 경로로 테스트를 구성할 수 없음.

    @Test
    void 마감된_경매면_예외가_발생한다() throws InterruptedException {
        Auction auction = createAuction(TimeUtils.now().plusSeconds(1));

        User bidder = userRepository.save(UserFixture.create("bidder1@chalkak.com", "encoded-password", "010-1111-1111"));
        Long bidderId = bidder.getId();
        pointService.charge(bidderId, BigDecimal.valueOf(50_000));
        BigDecimal bidAmount = BigDecimal.valueOf(2_000);
        Long auctionId = auction.getId();
        BidRequest request = new BidRequest(bidAmount);

        Thread.sleep(2_000);

        assertThatThrownBy(() -> bidService.submit(auctionId, bidderId, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", AuctionErrorCode.AUCTION_ALREADY_CLOSED);
    }

    @Test
    void 본인_경매에는_입찰할_수_없다() {
        Auction auction = createAuction();
        User owner = auction.getCamera().getOwner();
        BigDecimal bidAmount = BigDecimal.valueOf(2_000);
        Long auctionId = auction.getId();
        Long ownerId = owner.getId();
        BidRequest request = new BidRequest(bidAmount);

        assertThatThrownBy(() -> bidService.submit(auctionId, ownerId, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", AuctionErrorCode.SELF_BID_NOT_ALLOWED);
    }

    @Test
    void 존재하지_않는_회원이면_예외가_발생한다() {
        Auction auction = createAuction();
        BigDecimal bidAmount = BigDecimal.valueOf(2_000);
        Long auctionId = auction.getId();
        BidRequest request = new BidRequest(bidAmount);

        assertThatThrownBy(() -> bidService.submit(auctionId, -1L, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.NOT_FOUND)
            .hasMessage("회원 정보가 존재하지 않습니다.");
    }

    @Test
    void 현재가보다_낮거나_같은_입찰이면_예외가_발생한다() {
        Auction auction = createAuction();

        User bidder = userRepository.save(UserFixture.create("bidder1@chalkak.com", "encoded-password", "010-1111-1111"));
        Long bidderId = bidder.getId();
        pointService.charge(bidderId, BigDecimal.valueOf(50_000));
        Long auctionId = auction.getId();
        BidRequest request = new BidRequest(BigDecimal.valueOf(1_000));

        assertThatThrownBy(() -> bidService.submit(auctionId, bidderId, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", AuctionErrorCode.BID_AMOUNT_TOO_LOW);
    }

    @Test
    void 가용_포인트가_부족하면_예외가_발생한다() {
        Auction auction = createAuction();

        User bidder = userRepository.save(UserFixture.create("bidder1@chalkak.com", "encoded-password", "010-1111-1111"));
        Long bidderId = bidder.getId();
        BigDecimal bidAmount = BigDecimal.valueOf(2_000);
        pointService.charge(bidderId, BigDecimal.valueOf(1_999));
        Long auctionId = auction.getId();
        BidRequest request = new BidRequest(bidAmount);

        assertThatThrownBy(() -> bidService.submit(auctionId, bidderId, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", PointErrorCode.INSUFFICIENT_AVAILABLE_AMOUNT);
    }
}
