package com.chalkak.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.chalkak.common.exception.BusinessException;
import com.chalkak.user.fixture.UserFixture;

class UserTest {

    @Test
    void 유효한_값이면_User가_생성된다() {
        User user = UserFixture.create();

        assertThat(user.getEmail()).isEqualTo(UserFixture.DEFAULT_EMAIL);
        assertThat(user.getPassword()).isEqualTo(UserFixture.DEFAULT_ENCODED_PASSWORD);
        assertThat(user.getPhone()).isEqualTo(UserFixture.DEFAULT_PHONE);
    }

    @Test
    void 이메일_형식이_올바르지_않으면_예외가_발생한다() {
        assertThatThrownBy(() -> UserFixture.create("invalid-email", UserFixture.DEFAULT_ENCODED_PASSWORD, UserFixture.DEFAULT_PHONE))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void 전화번호_형식이_올바르지_않으면_예외가_발생한다() {
        assertThatThrownBy(() -> UserFixture.create(UserFixture.DEFAULT_EMAIL, UserFixture.DEFAULT_ENCODED_PASSWORD, "01012345678"))
            .isInstanceOf(BusinessException.class);
    }
}
