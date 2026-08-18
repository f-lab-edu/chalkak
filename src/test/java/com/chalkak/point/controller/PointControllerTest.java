package com.chalkak.point.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.auth.controller.request.AuthRequest;
import com.chalkak.point.controller.request.PointRequest;
import com.chalkak.point.entity.Point;
import com.chalkak.point.repository.PointRepository;
import com.chalkak.user.entity.User;
import com.chalkak.user.fixture.UserFixture;
import com.chalkak.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 포인트_조회시_Point_row가_없으면_0원을_응답한다() throws Exception {
        User user = userRepository.save(UserFixture.create(
            UserFixture.DEFAULT_EMAIL, passwordEncoder.encode(UserFixture.DEFAULT_RAW_PASSWORD), UserFixture.DEFAULT_PHONE));
        MockHttpSession session = login(UserFixture.DEFAULT_EMAIL);

        mockMvc.perform(get("/api/v1/points")
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(user.getId()))
            .andExpect(jsonPath("$.availableAmount").value(0))
            .andExpect(jsonPath("$.lockedAmount").value(0));
    }

    @Test
    void 포인트_조회시_충전한_금액이_응답된다() throws Exception {
        User user = userRepository.save(UserFixture.create(
            UserFixture.DEFAULT_EMAIL, passwordEncoder.encode(UserFixture.DEFAULT_RAW_PASSWORD), UserFixture.DEFAULT_PHONE));
        MockHttpSession session = login(UserFixture.DEFAULT_EMAIL);
        PointRequest request = new PointRequest(BigDecimal.valueOf(1_000));
        mockMvc.perform(post("/api/v1/points/charge")
            .session(session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        mockMvc.perform(get("/api/v1/points")
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(user.getId()))
            .andExpect(jsonPath("$.availableAmount").value(1_000));
    }

    @Test
    void 포인트_조회시_로그인하지_않으면_403을_응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/points"))
            .andExpect(status().isForbidden());
    }

    @Test
    void 처음_충전하면_200과_충전된_가용금액을_응답한다() throws Exception {
        User user = userRepository.save(UserFixture.create(
            UserFixture.DEFAULT_EMAIL, passwordEncoder.encode(UserFixture.DEFAULT_RAW_PASSWORD), UserFixture.DEFAULT_PHONE));
        MockHttpSession session = login(UserFixture.DEFAULT_EMAIL);
        PointRequest request = new PointRequest(BigDecimal.valueOf(1_000));

        mockMvc.perform(post("/api/v1/points/charge")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(user.getId()))
            .andExpect(jsonPath("$.availableAmount").value(1_000))
            .andExpect(jsonPath("$.lockedAmount").value(0));
    }

    @Test
    void 이미_포인트가_있으면_기존_가용금액에_누적된다() throws Exception {
        User user = userRepository.save(UserFixture.create(
            UserFixture.DEFAULT_EMAIL, passwordEncoder.encode(UserFixture.DEFAULT_RAW_PASSWORD), UserFixture.DEFAULT_PHONE));
        pointRepository.save(Point.open(user));
        MockHttpSession session = login(UserFixture.DEFAULT_EMAIL);
        PointRequest request = new PointRequest(BigDecimal.valueOf(1_000));

        mockMvc.perform(post("/api/v1/points/charge")
            .session(session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        mockMvc.perform(post("/api/v1/points/charge")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableAmount").value(2_000));

        assertThat(pointRepository.findByUserId(user.getId())).isPresent();
    }

    @Test
    void 충전_금액이_0이하이면_400을_응답한다() throws Exception {
        userRepository.save(UserFixture.create(
            UserFixture.DEFAULT_EMAIL, passwordEncoder.encode(UserFixture.DEFAULT_RAW_PASSWORD), UserFixture.DEFAULT_PHONE));
        MockHttpSession session = login(UserFixture.DEFAULT_EMAIL);
        PointRequest request = new PointRequest(BigDecimal.ZERO);

        mockMvc.perform(post("/api/v1/points/charge")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 로그인하지_않으면_403을_응답한다() throws Exception {
        PointRequest request = new PointRequest(BigDecimal.valueOf(1_000));

        mockMvc.perform(post("/api/v1/points/charge")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthRequest(email, UserFixture.DEFAULT_RAW_PASSWORD))))
            .andExpect(status().isOk())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
