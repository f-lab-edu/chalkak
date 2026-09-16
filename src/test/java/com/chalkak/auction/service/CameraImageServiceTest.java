package com.chalkak.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.auction.entity.Camera;
import com.chalkak.auction.entity.CameraImage;
import com.chalkak.auction.fixture.CameraFixture;
import com.chalkak.auction.fixture.MultipartFileFixture;
import com.chalkak.auction.repository.CameraImageRepository;
import com.chalkak.auction.repository.CameraRepository;
import com.chalkak.common.exception.BusinessException;
import com.chalkak.common.exception.CommonErrorCode;
import com.chalkak.file.exception.FileErrorCode;
import com.chalkak.file.service.FileStorage;
import com.chalkak.user.entity.User;
import com.chalkak.user.fixture.UserFixture;
import com.chalkak.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CameraImageServiceTest {

    @Autowired
    private CameraImageService cameraImageService;

    @Autowired
    private CameraImageRepository cameraImageRepository;

    @Autowired
    private CameraRepository cameraRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileStorage fileStorage;

    @Test
    void jpg_확장자면_IMAGE_JPEG_타입으로_응답한다() {
        CameraImage cameraImage = uploadAndAttach("photo.jpg");

        CameraImageDownload download = cameraImageService.download(cameraImage.getId());

        assertThat(download.contentType()).isEqualTo(MediaType.IMAGE_JPEG);
    }

    @Test
    void png_확장자면_IMAGE_PNG_타입으로_응답한다() {
        CameraImage cameraImage = uploadAndAttach("photo.png");

        CameraImageDownload download = cameraImageService.download(cameraImage.getId());

        assertThat(download.contentType()).isEqualTo(MediaType.IMAGE_PNG);
    }

    @Test
    void gif_확장자면_IMAGE_GIF_타입으로_응답한다() {
        CameraImage cameraImage = uploadAndAttach("photo.gif");

        CameraImageDownload download = cameraImageService.download(cameraImage.getId());

        assertThat(download.contentType()).isEqualTo(MediaType.IMAGE_GIF);
    }

    @Test
    void webp_확장자면_image_webp_타입으로_응답한다() {
        CameraImage cameraImage = uploadAndAttach("photo.webp");

        CameraImageDownload download = cameraImageService.download(cameraImage.getId());

        assertThat(download.contentType()).isEqualTo(MediaType.valueOf("image/webp"));
    }

    @Test
    void 알수없는_확장자면_APPLICATION_OCTET_STREAM_타입으로_응답한다() {
        CameraImage cameraImage = uploadAndAttach("photo.heic");

        CameraImageDownload download = cameraImageService.download(cameraImage.getId());

        assertThat(download.contentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }

    @Test
    void 존재하지_않는_이미지를_조회하면_예외가_발생한다() {
        assertThatThrownBy(() -> cameraImageService.download(-1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.NOT_FOUND)
            .hasMessage("이미지 정보가 존재하지 않습니다.");
    }

    @Test
    void DB에는_있지만_디스크에_파일이_없으면_예외가_발생한다() {
        User owner = userRepository.save(UserFixture.create());
        Camera camera = cameraRepository.save(CameraFixture.create(owner));
        CameraImage cameraImage = cameraImageRepository.save(CameraImage.attach(camera, "not-uploaded.jpg"));

        assertThatThrownBy(() -> cameraImageService.download(cameraImage.getId()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", FileErrorCode.FILE_NOT_FOUND);
    }

    private CameraImage uploadAndAttach(String filename) {
        User owner = userRepository.save(UserFixture.create());
        Camera camera = cameraRepository.save(CameraFixture.create(owner));
        String key = fileStorage.upload(MultipartFileFixture.image(filename));
        return cameraImageRepository.save(CameraImage.attach(camera, key));
    }
}
