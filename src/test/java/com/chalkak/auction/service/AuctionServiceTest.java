package com.chalkak.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.auction.controller.request.AuctionRequest;
import com.chalkak.auction.controller.response.AuctionDetailResponse;
import com.chalkak.auction.controller.response.AuctionResponse;
import com.chalkak.auction.controller.response.AuctionStatusResponse;
import com.chalkak.auction.controller.response.AuctionSummaryResponse;
import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.entity.AuctionStatus;
import com.chalkak.auction.entity.Camera;
import com.chalkak.auction.exception.AuctionErrorCode;
import com.chalkak.auction.fixture.AuctionFixture;
import com.chalkak.auction.fixture.AuctionRequestFixture;
import com.chalkak.auction.fixture.CameraFixture;
import com.chalkak.auction.fixture.MultipartFileFixture;
import com.chalkak.auction.repository.AuctionRepository;
import com.chalkak.auction.repository.CameraImageRepository;
import com.chalkak.auction.repository.CameraRepository;
import com.chalkak.bid.entity.BidRequestResult;
import com.chalkak.bid.repository.BidRequestResultRepository;
import com.chalkak.common.exception.BusinessException;
import com.chalkak.common.exception.CommonErrorCode;
import com.chalkak.common.response.PageResponse;
import com.chalkak.common.util.ImageUrls;
import com.chalkak.outbox.entity.Outbox;
import com.chalkak.outbox.repository.OutboxRepository;
import com.chalkak.user.entity.User;
import com.chalkak.user.fixture.UserFixture;
import com.chalkak.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuctionServiceTest {

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CameraImageRepository cameraImageRepository;

    @Autowired
    private CameraRepository cameraRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private BidRequestResultRepository bidRequestResultRepository;

    @Test
    void 정상_등록하면_Camera_CameraImage_Auction이_모두_저장된다() {
        User owner = userRepository.save(UserFixture.create());
        AuctionRequest request = AuctionRequestFixture.create();
        List<MultipartFile> images = MultipartFileFixture.images(3);

        AuctionResponse response = auctionService.register(owner.getId(), request, images);

        assertThat(response.status()).isEqualTo(AuctionStatus.IN_PROGRESS);
        assertThat(response.startPrice()).isEqualByComparingTo(request.startPrice());
        assertThat(response.currentPrice()).isEqualByComparingTo(request.startPrice());
        assertThat(cameraImageRepository.findAll()).hasSize(3);
    }

    @Test
    void 이미지가_3장_미만이면_예외가_발생한다() {
        User owner = userRepository.save(UserFixture.create());
        Long ownerId = owner.getId();
        AuctionRequest request = AuctionRequestFixture.create();
        List<MultipartFile> images = MultipartFileFixture.images(2);

        assertThatThrownBy(() -> auctionService.register(ownerId, request, images))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", AuctionErrorCode.INVALID_IMAGE_COUNT);
    }

    @Test
    void 존재하지_않는_판매자면_예외가_발생한다() {
        AuctionRequest request = AuctionRequestFixture.create();
        List<MultipartFile> images = MultipartFileFixture.images(3);

        assertThatThrownBy(() -> auctionService.register(-1L, request, images))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.NOT_FOUND)
            .hasMessage("판매자 정보가 존재하지 않습니다.");
    }

    @Test
    void 정상_조회하면_경매_상세_정보를_응답한다() {
        User owner = userRepository.save(UserFixture.create());
        AuctionRequest request = AuctionRequestFixture.create();
        List<MultipartFile> images = MultipartFileFixture.images(3);
        AuctionResponse registered = auctionService.register(owner.getId(), request, images);

        AuctionDetailResponse response = auctionService.getDetail(registered.id());

        assertThat(response.camera().category()).isEqualTo(request.category());
        assertThat(response.camera().brand()).isEqualTo(request.brand());
        assertThat(response.camera().modelName()).isEqualTo(request.modelName());
        assertThat(response.camera().conditionGrade()).isEqualTo(request.conditionGrade());
        assertThat(response.camera().description()).isEqualTo(request.description());
        assertThat(response.camera().imageUrls()).hasSize(3);
        assertThat(response.camera().imageUrls()).allMatch(url -> url.startsWith("/api/v1/images/"));
        assertThat(response.seller().id()).isEqualTo(owner.getId());
        assertThat(response.seller().nickname()).isEqualTo(owner.getNickname());
        assertThat(response.status()).isEqualTo(AuctionStatus.IN_PROGRESS);
    }

    @Test
    void 존재하지_않는_경매를_조회하면_예외가_발생한다() {
        assertThatThrownBy(() -> auctionService.getDetail(-1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.NOT_FOUND)
            .hasMessage("경매 정보가 존재하지 않습니다.");
    }

    @Test
    void 정상_조회하면_경매_상태_정보를_응답한다() {
        User owner = userRepository.save(UserFixture.create());
        AuctionRequest request = AuctionRequestFixture.create();
        List<MultipartFile> images = MultipartFileFixture.images(3);
        AuctionResponse registered = auctionService.register(owner.getId(), request, images);

        AuctionStatusResponse response = auctionService.getStatus(registered.id());

        assertThat(response.currentPrice()).isEqualByComparingTo(request.startPrice());
        assertThat(response.status()).isEqualTo(AuctionStatus.IN_PROGRESS);
        assertThat(response.closesAt()).isEqualTo(request.closesAt());
    }

    @Test
    void 존재하지_않는_경매의_상태를_조회하면_예외가_발생한다() {
        assertThatThrownBy(() -> auctionService.getStatus(-1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.NOT_FOUND)
            .hasMessage("경매 정보가 존재하지 않습니다.");
    }

    @Test
    void 정상_조회하면_경매_목록을_응답한다() {
        User owner = userRepository.save(UserFixture.create());
        AuctionRequest request = AuctionRequestFixture.create();
        auctionService.register(owner.getId(), request, MultipartFileFixture.images(3));

        PageResponse<AuctionSummaryResponse> response = auctionService.getAuctionsBySearchCondition(
            null, null, null, null, PageRequest.of(0, 10));

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).camera().brand()).isEqualTo(request.brand());
        assertThat(response.content().get(0).camera().modelName()).isEqualTo(request.modelName());
    }

    @Test
    void 응답의_썸네일은_가장_먼저_등록한_이미지다() {
        User owner = userRepository.save(UserFixture.create());
        AuctionRequest request = AuctionRequestFixture.create();
        AuctionResponse registered = auctionService.register(owner.getId(), request, MultipartFileFixture.images(3));
        String expectedThumbnail = ImageUrls.download(cameraImageRepository
            .findFirstByCameraIdOrderByIdAsc(registered.cameraId())
            .orElseThrow()
            .getId());

        PageResponse<AuctionSummaryResponse> response = auctionService.getAuctionsBySearchCondition(
            null, null, null, null, PageRequest.of(0, 10));

        assertThat(response.content().get(0).camera().thumbnailImage()).isEqualTo(expectedThumbnail);
    }

    @Test
    void 페이지네이션_메타데이터가_응답에_포함된다() {
        User owner = userRepository.save(UserFixture.create());
        for (int i = 0; i < 3; i++) {
            auctionService.register(owner.getId(), AuctionRequestFixture.create(), MultipartFileFixture.images(3));
        }

        PageResponse<AuctionSummaryResponse> response = auctionService.getAuctionsBySearchCondition(
            null, null, null, null, PageRequest.of(0, 2));

        assertThat(response.content()).hasSize(2);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    void 마감_시각이_지나고_입찰이_없으면_유찰로_변경된다() {
        User owner = userRepository.save(UserFixture.create());
        Camera camera = cameraRepository.save(CameraFixture.create(owner));
        Auction expired = auctionRepository.save(AuctionFixture.create(camera));
        ReflectionTestUtils.setField(expired, "extendedClosesAt", LocalDateTime.now().minusHours(1));

        auctionService.closeIfExpired(expired.getId());

        assertThat(auctionRepository.findById(expired.getId()).orElseThrow().getStatus())
            .isEqualTo(AuctionStatus.FAILED);
    }

    @Test
    void 마감_시각이_지나고_입찰이_있으면_낙찰로_변경된다() {
        User owner = userRepository.save(UserFixture.create());
        Camera camera = cameraRepository.save(CameraFixture.create(owner));
        Auction expired = auctionRepository.save(AuctionFixture.create(camera));
        expired.updateCurrentPrice(AuctionFixture.DEFAULT_START_PRICE.add(BigDecimal.valueOf(1_000)));
        ReflectionTestUtils.setField(expired, "extendedClosesAt", LocalDateTime.now().minusHours(1));

        auctionService.closeIfExpired(expired.getId());

        assertThat(auctionRepository.findById(expired.getId()).orElseThrow().getStatus())
            .isEqualTo(AuctionStatus.SUCCESSFUL);
    }

    @Test
    void 마감_시각이_지나지_않은_경매는_상태가_변경되지_않는다() {
        User owner = userRepository.save(UserFixture.create());
        Camera camera = cameraRepository.save(CameraFixture.create(owner));
        Auction notExpired = auctionRepository.save(AuctionFixture.create(camera));

        auctionService.closeIfExpired(notExpired.getId());

        assertThat(auctionRepository.findById(notExpired.getId()).orElseThrow().getStatus())
            .isEqualTo(AuctionStatus.IN_PROGRESS);
    }

    @Test
    void 마감_대상_조회는_마감_시각이_지난_진행중_경매만_반환한다() {
        User owner = userRepository.save(UserFixture.create());
        Camera camera = cameraRepository.save(CameraFixture.create(owner));
        Auction expired = auctionRepository.save(AuctionFixture.create(camera));
        ReflectionTestUtils.setField(expired, "extendedClosesAt", LocalDateTime.now().minusHours(1));
        Auction closed = auctionRepository.save(AuctionFixture.create(camera));
        ReflectionTestUtils.setField(closed, "extendedClosesAt", LocalDateTime.now().minusHours(1));
        closed.close();
        auctionRepository.save(AuctionFixture.create(camera));

        List<Long> expiredIds = auctionService.findExpiredAuctionIds();

        assertThat(expiredIds).containsExactly(expired.getId());
    }

    @Test
    void 마감_시각_후_대기_시간_안이면_마감_대상이_아니다() {
        Auction waiting = auctionRepository.save(AuctionFixture.create(createCamera()));
        ReflectionTestUtils.setField(waiting, "extendedClosesAt", LocalDateTime.now().minusSeconds(1));

        List<Long> expiredIds = auctionService.findExpiredAuctionIds();

        assertThat(expiredIds).doesNotContain(waiting.getId());
    }

    @Test
    void 마감_시각_후_대기_시간_안이면_마감하지_않는다() {
        Auction waiting = auctionRepository.save(AuctionFixture.create(createCamera()));
        ReflectionTestUtils.setField(waiting, "extendedClosesAt", LocalDateTime.now().minusSeconds(1));

        auctionService.closeIfExpired(waiting.getId());

        assertThat(auctionRepository.findById(waiting.getId()).orElseThrow().getStatus())
            .isEqualTo(AuctionStatus.IN_PROGRESS);
    }

    @Test
    void 결과가_없는_입찰_요청이_있으면_마감하지_않는다() {
        Auction expired = createExpiredAuction(createCamera());
        saveRequest(expired.getId());

        auctionService.closeIfExpired(expired.getId());

        assertThat(auctionRepository.findById(expired.getId()).orElseThrow().getStatus())
            .isEqualTo(AuctionStatus.IN_PROGRESS);
    }

    @Test
    void 모든_입찰_요청에_결과가_있으면_마감한다() {
        Auction expired = createExpiredAuction(createCamera());
        Outbox request = saveRequest(expired.getId());
        bidRequestResultRepository.save(BidRequestResult.accepted(
            request.getRequestId(), expired.getId(), 1L, BigDecimal.valueOf(15_000), 1L));

        auctionService.closeIfExpired(expired.getId());

        assertThat(auctionRepository.findById(expired.getId()).orElseThrow().getStatus())
            .isEqualTo(AuctionStatus.FAILED);
    }

    @Test
    void 발행을_포기한_요청은_실패_결과가_있어_마감을_막지_않는다() {
        Auction expired = createExpiredAuction(createCamera());
        Outbox request = saveRequest(expired.getId());
        request.markFailed();
        bidRequestResultRepository.save(BidRequestResult.rejected(
            request.getRequestId(), expired.getId(), 1L, BigDecimal.valueOf(15_000), "BID-002"));

        auctionService.closeIfExpired(expired.getId());

        assertThat(auctionRepository.findById(expired.getId()).orElseThrow().getStatus())
            .isEqualTo(AuctionStatus.FAILED);
    }

    @Test
    void 다른_경매의_결과가_없는_요청은_마감을_막지_않는다() {
        Camera camera = createCamera();
        Auction expired = createExpiredAuction(camera);
        Auction other = createExpiredAuction(camera);
        saveRequest(other.getId());

        auctionService.closeIfExpired(expired.getId());

        assertThat(auctionRepository.findById(expired.getId()).orElseThrow().getStatus())
            .isEqualTo(AuctionStatus.FAILED);
    }

    private Camera createCamera() {
        User owner = userRepository.save(UserFixture.create());
        return cameraRepository.save(CameraFixture.create(owner));
    }

    private Auction createExpiredAuction(Camera camera) {
        Auction auction = auctionRepository.save(AuctionFixture.create(camera));
        ReflectionTestUtils.setField(auction, "extendedClosesAt", LocalDateTime.now().minusHours(1));
        return auction;
    }

    private Outbox saveRequest(Long auctionId) {
        return outboxRepository.save(Outbox.pending(UUID.randomUUID(), auctionId, 1L, BigDecimal.valueOf(15_000), "{}"));
    }
}
