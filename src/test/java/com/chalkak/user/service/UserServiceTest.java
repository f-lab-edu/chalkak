package com.chalkak.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.chalkak.common.exception.BusinessException;
import com.chalkak.user.entity.User;
import com.chalkak.user.exception.UserErrorCode;
import com.chalkak.user.fixture.UserRequestFixture;
import com.chalkak.user.repository.UserRepository;

@SpringBootTest
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void 회원가입에_성공하면_비밀번호가_인코딩되어_저장된다() {
        userService.signUp(UserRequestFixture.create());

        User user = userRepository.findAll().get(0);
        assertThat(user.getEmail()).isEqualTo(UserRequestFixture.DEFAULT_EMAIL);
        assertThat(user.getPassword()).isNotEqualTo(UserRequestFixture.DEFAULT_PASSWORD);
        assertThat(passwordEncoder.matches(UserRequestFixture.DEFAULT_PASSWORD, user.getPassword())).isTrue();
    }

    @Test
    void 이미_가입된_이메일이면_예외가_발생한다() {
        userService.signUp(UserRequestFixture.create());

        assertThatThrownBy(() -> userService.signUp(
            UserRequestFixture.create(UserRequestFixture.DEFAULT_EMAIL, UserRequestFixture.DEFAULT_PASSWORD, "010-9999-9999")))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void 이미_가입된_전화번호면_예외가_발생한다() {
        userService.signUp(UserRequestFixture.create());

        assertThatThrownBy(() -> userService.signUp(
            UserRequestFixture.create("other@chalkak.com", UserRequestFixture.DEFAULT_PASSWORD, UserRequestFixture.DEFAULT_PHONE)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.DUPLICATE_PHONE);
    }
}
