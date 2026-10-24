package com.chalkak.bid.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import com.chalkak.bid.queue.BidMessage;
import com.chalkak.bid.queue.BidMessageProducer;
import com.chalkak.common.util.TimeUtils;
import com.chalkak.user.entity.User;
import com.chalkak.user.fixture.UserFixture;
import com.chalkak.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
    private static final String OTHER_BIDDER_EMAIL = "other@chalkak.com";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

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

    @MockitoBean
    private BidMessageProducer bidMessageProducer;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 정상_입찰이면_202와_요청_ID를_응답하고_입찰_메시지를_발행한다() throws Exception {
        User owner = registerUser(OWNER_EMAIL);
        Auction auction = createAuction(owner);
        User bidder = registerUser(BIDDER_EMAIL);
        MockHttpSession session = login(BIDDER_EMAIL);

        MvcResult result = mockMvc.perform(post("/api/v1/auctions/{auctionId}/bids", auction.getId())
                .header(IDEMPOTENCY_KEY, UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BidRequest(BigDecimal.valueOf(15_000))))
                .session(session)
                .with(csrf()))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.requestId").exists())
            .andReturn();

        UUID requestId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.requestId"));
        ArgumentCaptor<BidMessage> captor = ArgumentCaptor.forClass(BidMessage.class);
        verify(bidMessageProducer).send(captor.capture());
        BidMessage message = captor.getValue();
        assertThat(message.requestId()).isEqualTo(requestId);
        assertThat(message.auctionId()).isEqualTo(auction.getId());
        assertThat(message.bidderId()).isEqualTo(bidder.getId());
        assertThat(message.bidAmount()).isEqualByComparingTo("15000");
    }

    @Test
    void 로그인하지_않으면_403을_응답한다() throws Exception {
        User owner = registerUser(OWNER_EMAIL);
        Auction auction = createAuction(owner);

        mockMvc.perform(post("/api/v1/auctions/{auctionId}/bids", auction.getId())
                .header(IDEMPOTENCY_KEY, UUID.randomUUID())
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
                .header(IDEMPOTENCY_KEY, UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BidRequest(BigDecimal.valueOf(0))))
                .session(session)
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 입찰_금액에_소수가_포함되면_400을_응답한다() throws Exception {
        User owner = registerUser(OWNER_EMAIL);
        Auction auction = createAuction(owner);
        registerUser(BIDDER_EMAIL);
        MockHttpSession session = login(BIDDER_EMAIL);

        mockMvc.perform(post("/api/v1/auctions/{auctionId}/bids", auction.getId())
                .header(IDEMPOTENCY_KEY, UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BidRequest(BigDecimal.valueOf(15_000.5))))
                .session(session)
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 같은_멱등키로_다시_보내면_같은_요청_ID로_발행한다() throws Exception {
        User owner = registerUser(OWNER_EMAIL);
        Auction auction = createAuction(owner);
        registerUser(BIDDER_EMAIL);
        MockHttpSession session = login(BIDDER_EMAIL);
        UUID idempotencyKey = UUID.randomUUID();

        UUID firstRequestId = bid(session, auction.getId(), idempotencyKey);
        UUID retryRequestId = bid(session, auction.getId(), idempotencyKey);

        assertThat(retryRequestId).isEqualTo(firstRequestId);
        ArgumentCaptor<BidMessage> captor = ArgumentCaptor.forClass(BidMessage.class);
        verify(bidMessageProducer, times(2)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(BidMessage::requestId).containsOnly(firstRequestId);
    }

    @Test
    void 다른_사용자가_같은_멱등키를_보내면_요청_ID가_다르다() throws Exception {
        User owner = registerUser(OWNER_EMAIL);
        Auction auction = createAuction(owner);
        registerUser(BIDDER_EMAIL);
        registerUser(OTHER_BIDDER_EMAIL);
        UUID idempotencyKey = UUID.randomUUID();

        UUID bidderRequestId = bid(login(BIDDER_EMAIL), auction.getId(), idempotencyKey);
        UUID otherBidderRequestId = bid(login(OTHER_BIDDER_EMAIL), auction.getId(), idempotencyKey);

        assertThat(otherBidderRequestId).isNotEqualTo(bidderRequestId);
    }

    @Test
    void 멱등키_헤더가_없으면_400을_응답한다() throws Exception {
        User owner = registerUser(OWNER_EMAIL);
        Auction auction = createAuction(owner);
        registerUser(BIDDER_EMAIL);
        MockHttpSession session = login(BIDDER_EMAIL);

        mockMvc.perform(post("/api/v1/auctions/{auctionId}/bids", auction.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BidRequest(BigDecimal.valueOf(15_000))))
                .session(session)
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 멱등키가_UUID_형식이_아니면_400을_응답한다() throws Exception {
        User owner = registerUser(OWNER_EMAIL);
        Auction auction = createAuction(owner);
        registerUser(BIDDER_EMAIL);
        MockHttpSession session = login(BIDDER_EMAIL);

        mockMvc.perform(post("/api/v1/auctions/{auctionId}/bids", auction.getId())
                .header(IDEMPOTENCY_KEY, "not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BidRequest(BigDecimal.valueOf(15_000))))
                .session(session)
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }

    private UUID bid(MockHttpSession session, Long auctionId, UUID idempotencyKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auctions/{auctionId}/bids", auctionId)
                .header(IDEMPOTENCY_KEY, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BidRequest(BigDecimal.valueOf(15_000))))
                .session(session)
                .with(csrf()))
            .andExpect(status().isAccepted())
            .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.requestId"));
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
        if (email.equals(OWNER_EMAIL)) {
            return "010-1111-1111";
        }
        return email.equals(BIDDER_EMAIL) ? "010-2222-2222" : "010-3333-3333";
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
