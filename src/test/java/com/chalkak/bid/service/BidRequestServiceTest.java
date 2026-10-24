package com.chalkak.bid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.auction.exception.AuctionErrorCode;
import com.chalkak.bid.entity.BidRequestResult;
import com.chalkak.bid.entity.BidRequestStatus;
import com.chalkak.bid.queue.BidMessage;
import com.chalkak.bid.repository.BidRequestResultRepository;
import com.chalkak.common.exception.BusinessException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

// 재전달된 메시지가 각자 다른 트랜잭션에서 저장되는 상황을 재현하므로 테스트 전체를 한 트랜잭션으로 처리X
@SpringBootTest
@ActiveProfiles("test")
class BidRequestServiceTest {

    @Autowired
    private BidRequestService bidRequestService;

    @Autowired
    private BidRequestResultRepository bidRequestResultRepository;

    @AfterEach
    void cleanUp() {
        bidRequestResultRepository.deleteAll();
    }

    @Test
    void 이미_성공한_요청이_다시_전달돼_거절로_저장되면_PK_중복으로_실패하고_성공_결과가_유지된다() {
        BidMessage message = createMessage();
        bidRequestResultRepository.save(BidRequestResult.accepted(
            message.requestId(), message.auctionId(), message.bidderId(), message.bidAmount(), 1L));
        BusinessException rejectedByPrice = new BusinessException(AuctionErrorCode.BID_AMOUNT_TOO_LOW);

        assertThatThrownBy(() -> bidRequestService.reject(message, rejectedByPrice))
            .isInstanceOf(DataIntegrityViolationException.class);

        BidRequestResult result = bidRequestResultRepository.findById(message.requestId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(BidRequestStatus.ACCEPTED);
        assertThat(result.getBidId()).isEqualTo(1L);
        assertThat(result.getErrorCode()).isNull();
    }

    @Test
    void 같은_요청의_결과를_두_번_저장해도_결과는_한_건만_남는다() {
        BidMessage message = createMessage();
        bidRequestResultRepository.save(BidRequestResult.accepted(
            message.requestId(), message.auctionId(), message.bidderId(), message.bidAmount(), 1L));
        BidRequestResult duplicate = BidRequestResult.accepted(
            message.requestId(), message.auctionId(), message.bidderId(), message.bidAmount(), 2L);

        assertThatThrownBy(() -> bidRequestResultRepository.save(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(bidRequestResultRepository.count()).isEqualTo(1);
        assertThat(bidRequestResultRepository.findById(message.requestId()).orElseThrow().getBidId()).isEqualTo(1L);
    }

    private BidMessage createMessage() {
        return new BidMessage(UUID.randomUUID(), 1L, 1L, BigDecimal.valueOf(10_000));
    }
}
