package com.chalkak.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.chalkak.user.controller.request.UserRequest;
import com.chalkak.user.fixture.UserRequestFixture;
import com.chalkak.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 회원가입에_성공하면_204를_응답한다() throws Exception {
        UserRequest request = UserRequestFixture.create();

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNoContent());

        assertThat(userRepository.existsByEmail(UserRequestFixture.DEFAULT_EMAIL)).isTrue();
    }

    @Test
    void 이메일_형식이_올바르지_않으면_400을_응답한다() throws Exception {
        UserRequest request = UserRequestFixture.create(
            "invalid-email", UserRequestFixture.DEFAULT_PASSWORD, UserRequestFixture.DEFAULT_PHONE);

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 이미_가입된_이메일이면_409를_응답한다() throws Exception {
        UserRequest request = UserRequestFixture.create();
        String json = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("USER-003"));
    }
}
