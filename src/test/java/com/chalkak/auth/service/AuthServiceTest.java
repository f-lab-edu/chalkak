package com.chalkak.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.chalkak.auth.controller.request.AuthRequest;
import com.chalkak.user.fixture.UserFixture;
import com.chalkak.user.repository.UserRepository;

@SpringBootTest
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 로그인에_성공하면_세션에_인증_정보가_저장된다() {
        userRepository.save(UserFixture.create(
            UserFixture.DEFAULT_EMAIL, passwordEncoder.encode("raw-password"), UserFixture.DEFAULT_PHONE));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        authService.login(new AuthRequest(UserFixture.DEFAULT_EMAIL, "raw-password"), request, response);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
        assertThat(request.getSession(false)).isNotNull();
    }

    @Test
    void 비밀번호가_틀리면_예외가_발생한다() {
        userRepository.save(UserFixture.create(
            UserFixture.DEFAULT_EMAIL, passwordEncoder.encode("raw-password"), UserFixture.DEFAULT_PHONE));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> authService.login(
            new AuthRequest(UserFixture.DEFAULT_EMAIL, "wrong-password"), request, response))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void 존재하지_않는_이메일이면_예외가_발생한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> authService.login(
            new AuthRequest("nobody@chalkak.com", "raw-password"), request, response))
            .isInstanceOf(BadCredentialsException.class);
    }
}
