package com.chalkak.outbox.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chalkak.bid.entity.BidRequestResult;
import com.chalkak.bid.entity.BidRequestStatus;
import com.chalkak.bid.exception.BidErrorCode;
import com.chalkak.bid.queue.BidMessage;
import com.chalkak.bid.queue.BidMessageProducer;
import com.chalkak.bid.repository.BidRequestResultRepository;
import com.chalkak.common.util.TimeUtils;
import com.chalkak.outbox.entity.Outbox;
import com.chalkak.outbox.entity.OutboxStatus;
import com.chalkak.outbox.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "scheduler.outbox-relay.batch-size=2")
@ActiveProfiles("test")
class OutboxRelayTest {

  @Autowired
  private OutboxRelay outboxRelay;

  @Autowired
  private OutboxRepository outboxRepository;

  @Autowired
  private BidRequestResultRepository bidRequestResultRepository;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private BidMessageProducer bidMessageProducer;

  @AfterEach
  void cleanUp() {
    outboxRepository.deleteAll();
    bidRequestResultRepository.deleteAll();
  }

  @Test
  void PENDING을_오래된_순으로_모두_발행하고_PUBLISHED로_바꾼다() throws JsonProcessingException {
    Outbox first = savePending();
    Outbox second = savePending();
    Outbox third = savePending();

    outboxRelay.relay();

    InOrder inOrder = inOrder(bidMessageProducer);
    inOrder.verify(bidMessageProducer).send(argThat(m -> m.requestId().equals(first.getRequestId())));
    inOrder.verify(bidMessageProducer).send(argThat(m -> m.requestId().equals(second.getRequestId())));
    inOrder.verify(bidMessageProducer).send(argThat(m -> m.requestId().equals(third.getRequestId())));
    assertThat(outboxRepository.findAll())
        .allSatisfy(outbox -> {
          assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
          assertThat(outbox.getPublishedAt()).isNotNull();
        });
  }

  @Test
  void 발행에_실패하면_그_자리에서_멈추고_다음_실행에서_그_행부터_다시_발행한다() throws JsonProcessingException {
    Outbox first = savePending();
    Outbox second = savePending();
    Outbox third = savePending();
    willThrow(new RuntimeException("sqs down")).willDoNothing()
        .given(bidMessageProducer).send(argThat(m -> m != null && m.requestId().equals(second.getRequestId())));

    outboxRelay.relay();

    assertThat(statusOf(first)).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(statusOf(second)).isEqualTo(OutboxStatus.PENDING);
    assertThat(statusOf(third)).isEqualTo(OutboxStatus.PENDING);
    verify(bidMessageProducer, never()).send(argThat(m -> m != null && m.requestId().equals(third.getRequestId())));

    outboxRelay.relay();

    assertThat(statusOf(second)).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(statusOf(third)).isEqualTo(OutboxStatus.PUBLISHED);
  }

  @Test
  void payload를_읽을_수_없으면_FAILED로_바꾸고_입찰_실패_결과를_저장한_뒤_다음_행을_발행한다() throws JsonProcessingException {
    Outbox broken = outboxRepository.save(
        Outbox.pending(UUID.randomUUID(), 1L, 7L, BigDecimal.valueOf(12_000), "not-json"));
    Outbox next = savePending();
    willDoNothing().given(bidMessageProducer).send(any());

    outboxRelay.relay();

    assertThat(statusOf(broken)).isEqualTo(OutboxStatus.FAILED);
    BidRequestResult result = bidRequestResultRepository.findById(broken.getRequestId()).orElseThrow();
    assertThat(result.getStatus()).isEqualTo(BidRequestStatus.REJECTED);
    assertThat(result.getErrorCode()).isEqualTo(BidErrorCode.BID_NOT_PROCESSED.getCode());
    assertThat(result.getBidderId()).isEqualTo(7L);
    assertThat(result.getBidAmount()).isEqualByComparingTo("12000");
    assertThat(statusOf(next)).isEqualTo(OutboxStatus.PUBLISHED);
  }

  @Test
  void 입찰_실패_결과_저장에_실패하면_FAILED_변경도_롤백되어_PENDING으로_남는다() {
    UUID requestId = UUID.randomUUID();
    Outbox broken = outboxRepository.save(Outbox.pending(requestId, 1L, 7L, BigDecimal.valueOf(12_000), "not-json"));
    bidRequestResultRepository.save(BidRequestResult.accepted(requestId, 1L, 7L, BigDecimal.valueOf(12_000), 1L));

    assertThatThrownBy(() -> outboxRelay.relay())
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(statusOf(broken)).isEqualTo(OutboxStatus.PENDING);
  }

  private Outbox savePending() throws JsonProcessingException {
    UUID requestId = UUID.randomUUID();
    BidMessage message = new BidMessage(requestId, 1L, 1L, BigDecimal.valueOf(10_000), TimeUtils.now());
    return outboxRepository.save(
        Outbox.pending(requestId, 1L, 1L, BigDecimal.valueOf(10_000), objectMapper.writeValueAsString(message)));
  }

  private OutboxStatus statusOf(Outbox outbox) {
    return outboxRepository.findById(outbox.getId()).orElseThrow().getStatus();
  }
}
