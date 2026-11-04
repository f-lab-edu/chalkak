package com.chalkak.bid.queue;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chalkak.auction.exception.AuctionErrorCode;
import com.chalkak.bid.exception.BidErrorCode;
import com.chalkak.bid.service.BidRequestService;
import com.chalkak.common.exception.BusinessException;
import com.chalkak.common.util.TimeUtils;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.CannotCreateTransactionException;

@SpringBootTest
@ActiveProfiles("test")
class BidMessageConsumerTest {

    @Autowired
    private BidMessageConsumer bidMessageConsumer;

    @MockitoBean
    private BidRequestService bidRequestService;

    @MockitoBean
    private BidMessageProducer bidMessageProducer;

    static Stream<RuntimeException> retryableExceptions() {
        return Stream.of(
            new CannotAcquireLockException("lock"),
            new QueryTimeoutException("timeout"),
            new CannotCreateTransactionException("no connection"),
            new DataAccessResourceFailureException("connection lost"));
    }

    @ParameterizedTest
    @MethodSource("retryableExceptions")
    void 재시도_가능한_예외가_최대_수신_횟수_전이면_예외를_다시_던져_재시도한다(RuntimeException exception) {
        BidMessage bid = createBid();
        Message<BidMessage> message = MessageBuilder.withPayload(bid).build();
        willThrow(exception).given(bidRequestService).accept(bid);

        assertThatThrownBy(() -> bidMessageConsumer.consume(message, 2))
            .isSameAs(exception);

        verify(bidRequestService, never()).reject(any(), any());
        verify(bidMessageProducer, never()).sendToDLQ(any());
    }

    @Test
    void 재시도_가능한_예외가_마지막_수신이면_입찰_실패로_거절하고_DLQ로_보낸다() {
        BidMessage bid = createBid();
        Message<BidMessage> message = MessageBuilder.withPayload(bid).build();
        willThrow(new CannotAcquireLockException("lock")).given(bidRequestService).accept(bid);

        bidMessageConsumer.consume(message, 3);

        verify(bidRequestService).reject(bid, BidErrorCode.BID_NOT_PROCESSED);
        verify(bidMessageProducer).sendToDLQ(bid);
    }

    @Test
    void 재시도_불가능한_예외는_첫_수신에서_바로_입찰_실패로_거절하고_DLQ로_보낸다() {
        BidMessage bid = createBid();
        Message<BidMessage> message = MessageBuilder.withPayload(bid).build();
        willThrow(new DataIntegrityViolationException("constraint")).given(bidRequestService).accept(bid);

        bidMessageConsumer.consume(message, 1);

        verify(bidRequestService).reject(bid, BidErrorCode.BID_NOT_PROCESSED);
        verify(bidMessageProducer).sendToDLQ(bid);
    }

    @Test
    void 비즈니스_예외는_해당_코드로_거절하고_DLQ로_보내지_않는다() {
        BidMessage bid = createBid();
        Message<BidMessage> message = MessageBuilder.withPayload(bid).build();
        willThrow(new BusinessException(AuctionErrorCode.BID_AMOUNT_TOO_LOW)).given(bidRequestService).accept(bid);

        bidMessageConsumer.consume(message, 1);

        verify(bidRequestService).reject(bid, AuctionErrorCode.BID_AMOUNT_TOO_LOW);
        verify(bidMessageProducer, never()).sendToDLQ(any());
    }

    @Test
    void 이미_처리된_요청은_다시_처리하지_않는다() {
        BidMessage bid = createBid();
        Message<BidMessage> message = MessageBuilder.withPayload(bid).build();
        given(bidRequestService.exists(bid.requestId())).willReturn(true);

        bidMessageConsumer.consume(message, 2);

        verify(bidRequestService, never()).accept(any());
        verify(bidRequestService, never()).reject(any(), any());
    }

    @Test
    void 결과_조회_중_재시도_가능한_예외도_재시도한다() {
        BidMessage bid = createBid();
        Message<BidMessage> message = MessageBuilder.withPayload(bid).build();
        CannotCreateTransactionException exception = new CannotCreateTransactionException("no connection");
        willThrow(exception).given(bidRequestService).exists(bid.requestId());

        assertThatThrownBy(() -> bidMessageConsumer.consume(message, 1))
            .isSameAs(exception);

        verify(bidRequestService, never()).accept(any());
    }

    @Test
    void DLQ_전송에_실패해도_예외_없이_끝나_ack된다() {
        BidMessage bid = createBid();
        Message<BidMessage> message = MessageBuilder.withPayload(bid).build();
        willThrow(new IllegalStateException("bug")).given(bidRequestService).accept(bid);
        willThrow(new RuntimeException("sqs down")).given(bidMessageProducer).sendToDLQ(bid);

        assertThatCode(() -> bidMessageConsumer.consume(message, 1))
            .doesNotThrowAnyException();

        verify(bidRequestService).reject(bid, BidErrorCode.BID_NOT_PROCESSED);
    }

    @Test
    void 실패_결과_저장에_실패하면_예외를_던져_ack하지_않는다() {
        BidMessage bid = createBid();
        Message<BidMessage> message = MessageBuilder.withPayload(bid).build();
        DataAccessResourceFailureException exception = new DataAccessResourceFailureException("db down");
        willThrow(new IllegalStateException("bug")).given(bidRequestService).accept(bid);
        willThrow(exception).given(bidRequestService).reject(bid, BidErrorCode.BID_NOT_PROCESSED);

        assertThatThrownBy(() -> bidMessageConsumer.consume(message, 1))
            .isSameAs(exception);

        verify(bidMessageProducer, never()).sendToDLQ(any());
    }

    private BidMessage createBid() {
        return new BidMessage(UUID.randomUUID(), 1L, 1L, BigDecimal.valueOf(10_000), TimeUtils.now());
    }
}
