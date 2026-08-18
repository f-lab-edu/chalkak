package com.chalkak.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.chalkak.auth.controller.request.AuthRequest;
import com.chalkak.user.fixture.UserFixture;
import com.chalkak.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 로그인에_성공하면_200과_세션을_응답한다() throws Exception {
        userRepository.save(UserFixture.create(
            UserFixture.DEFAULT_EMAIL, passwordEncoder.encode(UserFixture.DEFAULT_RAW_PASSWORD), UserFixture.DEFAULT_PHONE));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthRequest(UserFixture.DEFAULT_EMAIL, UserFixture.DEFAULT_RAW_PASSWORD))))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(result.getRequest().getSession(false)).isNotNull();
    }

    @Test
    void 비밀번호가_틀리면_401을_응답한다() throws Exception {
        userRepository.save(UserFixture.create(
            UserFixture.DEFAULT_EMAIL, passwordEncoder.encode(UserFixture.DEFAULT_RAW_PASSWORD), UserFixture.DEFAULT_PHONE));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthRequest(UserFixture.DEFAULT_EMAIL, "wrong-password"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    @Test
    void 로그아웃하면_세션이_무효화된다() throws Exception {
        userRepository.save(UserFixture.create(
            UserFixture.DEFAULT_EMAIL, passwordEncoder.encode(UserFixture.DEFAULT_RAW_PASSWORD), UserFixture.DEFAULT_PHONE));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthRequest(UserFixture.DEFAULT_EMAIL, UserFixture.DEFAULT_RAW_PASSWORD))))
            .andExpect(status().isOk())
            .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
            .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
    }
}
