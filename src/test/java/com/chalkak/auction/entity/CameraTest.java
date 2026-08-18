package com.chalkak.auction.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.auction.exception.CameraErrorCode;
import com.chalkak.auction.fixture.CameraFixture;
import com.chalkak.common.exception.BusinessException;
import com.chalkak.user.entity.User;
import com.chalkak.user.fixture.UserFixture;
import org.junit.jupiter.api.Test;

class CameraTest {

    @Test
    void 유효한_값이면_Camera가_생성된다() {
        Camera camera = CameraFixture.create();

        assertThat(camera.getCategory()).isEqualTo(CameraFixture.DEFAULT_CATEGORY);
        assertThat(camera.getBrand()).isEqualTo(CameraFixture.DEFAULT_BRAND);
        assertThat(camera.getModelName()).isEqualTo(CameraFixture.DEFAULT_MODEL_NAME);
        assertThat(camera.getConditionGrade()).isEqualTo(CameraFixture.DEFAULT_CONDITION_GRADE);
        assertThat(camera.getDescription()).isEqualTo(CameraFixture.DEFAULT_DESCRIPTION);
    }

    @Test
    void owner가_null이면_예외가_발생한다() {
        assertThatThrownBy(() -> CameraFixture.create(null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CameraErrorCode.INVALID_OWNER);
    }

    @Test
    void category가_null이면_예외가_발생한다() {
        User owner = UserFixture.create();

        assertThatThrownBy(() -> CameraFixture.create(owner, null, CameraFixture.DEFAULT_BRAND,
            CameraFixture.DEFAULT_MODEL_NAME, CameraFixture.DEFAULT_CONDITION_GRADE, CameraFixture.DEFAULT_DESCRIPTION))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CameraErrorCode.INVALID_CATEGORY);
    }

    @Test
    void brand가_공백이면_예외가_발생한다() {
        User owner = UserFixture.create();

        assertThatThrownBy(() -> CameraFixture.create(owner, CameraFixture.DEFAULT_CATEGORY, " ",
            CameraFixture.DEFAULT_MODEL_NAME, CameraFixture.DEFAULT_CONDITION_GRADE, CameraFixture.DEFAULT_DESCRIPTION))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CameraErrorCode.INVALID_BRAND);
    }

    @Test
    void brand가_빈_문자열이면_예외가_발생한다() {
        User owner = UserFixture.create();

        assertThatThrownBy(() -> CameraFixture.create(owner, CameraFixture.DEFAULT_CATEGORY, "",
            CameraFixture.DEFAULT_MODEL_NAME, CameraFixture.DEFAULT_CONDITION_GRADE, CameraFixture.DEFAULT_DESCRIPTION))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CameraErrorCode.INVALID_BRAND);
    }

    @Test
    void modelName이_공백이면_예외가_발생한다() {
        User owner = UserFixture.create();

        assertThatThrownBy(() -> CameraFixture.create(owner, CameraFixture.DEFAULT_CATEGORY, CameraFixture.DEFAULT_BRAND,
            " ", CameraFixture.DEFAULT_CONDITION_GRADE, CameraFixture.DEFAULT_DESCRIPTION))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CameraErrorCode.INVALID_MODEL_NAME);
    }

    @Test
    void modelName이_빈_문자열이면_예외가_발생한다() {
        User owner = UserFixture.create();

        assertThatThrownBy(() -> CameraFixture.create(owner, CameraFixture.DEFAULT_CATEGORY, CameraFixture.DEFAULT_BRAND,
            "", CameraFixture.DEFAULT_CONDITION_GRADE, CameraFixture.DEFAULT_DESCRIPTION))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CameraErrorCode.INVALID_MODEL_NAME);
    }

    @Test
    void conditionGrade가_null이면_예외가_발생한다() {
        User owner = UserFixture.create();

        assertThatThrownBy(() -> CameraFixture.create(owner, CameraFixture.DEFAULT_CATEGORY, CameraFixture.DEFAULT_BRAND,
            CameraFixture.DEFAULT_MODEL_NAME, null, CameraFixture.DEFAULT_DESCRIPTION))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CameraErrorCode.INVALID_CONDITION_GRADE);
    }

    @Test
    void description이_공백이면_예외가_발생한다() {
        User owner = UserFixture.create();

        assertThatThrownBy(() -> CameraFixture.create(owner, CameraFixture.DEFAULT_CATEGORY, CameraFixture.DEFAULT_BRAND,
            CameraFixture.DEFAULT_MODEL_NAME, CameraFixture.DEFAULT_CONDITION_GRADE, " "))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CameraErrorCode.INVALID_DESCRIPTION);
    }

    @Test
    void description이_빈_문자열이면_예외가_발생한다() {
        User owner = UserFixture.create();

        assertThatThrownBy(() -> CameraFixture.create(owner, CameraFixture.DEFAULT_CATEGORY, CameraFixture.DEFAULT_BRAND,
            CameraFixture.DEFAULT_MODEL_NAME, CameraFixture.DEFAULT_CONDITION_GRADE, ""))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CameraErrorCode.INVALID_DESCRIPTION);
    }
}
