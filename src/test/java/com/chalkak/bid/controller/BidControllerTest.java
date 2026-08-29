package com.chalkak.bid.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.entity.Camera;
import com.chalkak.auction.fixture.AuctionFixture;
import com.chalkak.auction.fixture.CameraFixture;
import com.chalkak.auction.repository.AuctionRepository;
import com.chalkak.auction.repository.CameraRepository;
import com.chalkak.auth.controller.request.AuthRequest;
import com.chalkak.bid.controller.request.BidRequest;
import com.chalkak.common.util.TimeUtils;
import com.chalkak.point.service.PointService;
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
@ActiveProfiles("test")
@Transactional
class BidControllerTest {

    private static final String OWNER_EMAIL = "owner@chalkak.com";
    private static final String BIDDER_EMAIL = "bidder@chalkak.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CameraRepository cameraRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PointService pointService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 정상_입찰이면_201과_입찰_정보를_응답한다() throws Exception {
        User owner = registerUser(OWNER_EMAIL);
        Auction auction = createAuction(owner);
        User bidder = registerUser(BIDDER_EMAIL);
        pointService.charge(bidder.getId(), BigDecimal.valueOf(50_000));
        MockHttpSession session = login(BIDDER_EMAIL);

        mockMvc.perform(post("/api/v1/auctions/{auctionId}/bids", auction.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BidRequest(BigDecimal.valueOf(15_000))))
                .session(session)
                .with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.auctionId").value(auction.getId()))
            .andExpect(jsonPath("$.bidderId").value(bidder.getId()))
            .andExpect(jsonPath("$.bidAmount").value(15_000));
    }

    @Test
    void 로그인하지_않으면_403을_응답한다() throws Exception {
        User owner = registerUser(OWNER_EMAIL);
        Auction auction = createAuction(owner);

        mockMvc.perform(post("/api/v1/auctions/{auctionId}/bids", auction.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BidRequest(BigDecimal.valueOf(15_000))))
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void 입찰_금액이_0이하이면_400을_응답한다() throws Exception {
        User owner = registerUser(OWNER_EMAIL);
        Auction auction = createAuction(owner);
        registerUser(BIDDER_EMAIL);
        MockHttpSession session = login(BIDDER_EMAIL);

        mockMvc.perform(post("/api/v1/auctions/{auctionId}/bids", auction.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BidRequest(BigDecimal.valueOf(0))))
                .session(session)
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 존재하지_않는_경매면_404를_응답한다() throws Exception {
        registerUser(BIDDER_EMAIL);
        MockHttpSession session = login(BIDDER_EMAIL);

        mockMvc.perform(post("/api/v1/auctions/{auctionId}/bids", -1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BidRequest(BigDecimal.valueOf(15_000))))
                .session(session)
                .with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    void 본인_경매에는_입찰할_수_없다() throws Exception {
        User owner = registerUser(OWNER_EMAIL);
        Auction auction = createAuction(owner);
        MockHttpSession session = login(OWNER_EMAIL);

        mockMvc.perform(post("/api/v1/auctions/{auctionId}/bids", auction.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BidRequest(BigDecimal.valueOf(15_000))))
                .session(session)
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 현재가보다_낮거나_같은_입찰이면_400을_응답한다() throws Exception {
        User owner = registerUser(OWNER_EMAIL);
        Auction auction = createAuction(owner);
        User bidder = registerUser(BIDDER_EMAIL);
        pointService.charge(bidder.getId(), BigDecimal.valueOf(50_000));
        MockHttpSession session = login(BIDDER_EMAIL);

        mockMvc.perform(post("/api/v1/auctions/{auctionId}/bids", auction.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BidRequest(auction.getCurrentPrice())))
                .session(session)
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 가용_포인트가_부족하면_400을_응답한다() throws Exception {
        User owner = registerUser(OWNER_EMAIL);
        Auction auction = createAuction(owner);
        User bidder = registerUser(BIDDER_EMAIL);
        pointService.charge(bidder.getId(), BigDecimal.valueOf(1_000));
        MockHttpSession session = login(BIDDER_EMAIL);

        mockMvc.perform(post("/api/v1/auctions/{auctionId}/bids", auction.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BidRequest(BigDecimal.valueOf(15_000))))
                .session(session)
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }

    private User registerUser(String email) {
        return userRepository.save(
            UserFixture.create(email, passwordEncoder.encode(UserFixture.DEFAULT_RAW_PASSWORD), phone(email)));
    }

    private Auction createAuction(User owner) {
        Camera camera = cameraRepository.save(CameraFixture.create(owner));
        return auctionRepository.save(
            AuctionFixture.create(camera, BigDecimal.valueOf(10_000), TimeUtils.now().plusDays(3)));
    }

    private String phone(String email) {
        return email.equals(OWNER_EMAIL) ? "010-1111-1111" : "010-2222-2222";
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
