package com.chalkak.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.auction.controller.request.AuctionRequest;
import com.chalkak.auction.controller.response.AuctionDetailResponse;
import com.chalkak.auction.controller.response.AuctionResponse;
import com.chalkak.auction.controller.response.AuctionStatusResponse;
import com.chalkak.auction.entity.AuctionStatus;
import com.chalkak.auction.exception.AuctionErrorCode;
import com.chalkak.auction.fixture.AuctionRequestFixture;
import com.chalkak.auction.fixture.MultipartFileFixture;
import com.chalkak.auction.repository.CameraImageRepository;
import com.chalkak.common.exception.BusinessException;
import com.chalkak.common.exception.CommonErrorCode;
import com.chalkak.user.entity.User;
import com.chalkak.user.fixture.UserFixture;
import com.chalkak.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
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
        assertThat(response.camera().imageKeys()).hasSize(3);
        assertThat(response.seller().id()).isEqualTo(owner.getId());
        assertThat(response.seller().nickname()).isEqualTo("판매자" + owner.getId());
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
}
