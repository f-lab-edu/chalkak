package com.chalkak.auction.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chalkak.auction.fixture.AuctionRequestFixture;
import com.chalkak.auction.fixture.MultipartFileFixture;
import com.chalkak.auth.controller.request.AuthRequest;
import com.chalkak.user.fixture.UserFixture;
import com.chalkak.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuctionControllerTest {

    private static final String RAW_PASSWORD = "raw-password";

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
    void 정상_등록하면_201과_등록된_경매_정보를_응답한다() throws Exception {
        userRepository.save(UserFixture.create(
            UserFixture.DEFAULT_EMAIL, passwordEncoder.encode(RAW_PASSWORD), UserFixture.DEFAULT_PHONE));
        MockHttpSession session = login(UserFixture.DEFAULT_EMAIL);
        MockMultipartFile requestPart = requestPart();

        mockMvc.perform(multipart("/api/v1/auctions")
                .file(requestPart)
                .file(MultipartFileFixture.image("image1.jpg"))
                .file(MultipartFileFixture.image("image2.jpg"))
                .file(MultipartFileFixture.image("image3.jpg"))
                .session(session)
                .with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.startPrice").value(AuctionRequestFixture.DEFAULT_START_PRICE.intValue()))
            .andExpect(jsonPath("$.currentPrice").value(AuctionRequestFixture.DEFAULT_START_PRICE.intValue()));
    }

    @Test
    void 이미지가_3장_미만이면_400을_응답한다() throws Exception {
        userRepository.save(UserFixture.create(
            UserFixture.DEFAULT_EMAIL, passwordEncoder.encode(RAW_PASSWORD), UserFixture.DEFAULT_PHONE));
        MockHttpSession session = login(UserFixture.DEFAULT_EMAIL);
        MockMultipartFile requestPart = requestPart();

        mockMvc.perform(multipart("/api/v1/auctions")
                .file(requestPart)
                .file(MultipartFileFixture.image("image1.jpg"))
                .file(MultipartFileFixture.image("image2.jpg"))
                .session(session)
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 로그인하지_않으면_403을_응답한다() throws Exception {
        MockMultipartFile requestPart = requestPart();

        mockMvc.perform(multipart("/api/v1/auctions")
                .file(requestPart)
                .file(MultipartFileFixture.image("image1.jpg"))
                .file(MultipartFileFixture.image("image2.jpg"))
                .file(MultipartFileFixture.image("image3.jpg"))
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    private MockMultipartFile requestPart() throws Exception {
        return new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
            objectMapper.writeValueAsBytes(AuctionRequestFixture.create()));
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthRequest(email, RAW_PASSWORD))))
            .andExpect(status().isOk())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
