package com.chalkak.auction.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.auction.exception.CameraImageErrorCode;
import com.chalkak.auction.fixture.CameraFixture;
import com.chalkak.auction.fixture.CameraImageFixture;
import com.chalkak.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class CameraImageTest {

    @Test
    void 유효한_값이면_CameraImage가_생성된다() {
        CameraImage cameraImage = CameraImageFixture.create();

        assertThat(cameraImage.getImageKey()).isEqualTo(CameraImageFixture.DEFAULT_IMAGE_KEY);
    }

    @Test
    void imageKey가_공백이면_예외가_발생한다() {
        Camera camera = CameraFixture.create();

        assertThatThrownBy(() -> CameraImageFixture.create(camera, " "))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CameraImageErrorCode.INVALID_IMAGE_KEY);
    }

    @Test
    void imageKey가_빈_문자열이면_예외가_발생한다() {
        Camera camera = CameraFixture.create();

        assertThatThrownBy(() -> CameraImageFixture.create(camera, ""))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CameraImageErrorCode.INVALID_IMAGE_KEY);
    }
}
