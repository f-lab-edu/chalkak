package com.chalkak.bid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.auction.exception.AuctionErrorCode;
import com.chalkak.bid.controller.request.BidRequest;
import com.chalkak.bid.entity.BidRequestResult;
import com.chalkak.bid.exception.BidErrorCode;
import com.chalkak.bid.entity.BidRequestStatus;
import com.chalkak.bid.queue.BidMessage;
import com.chalkak.bid.repository.BidRequestResultRepository;
import com.chalkak.common.exception.BusinessException;
import com.chalkak.common.util.TimeUtils;
import com.chalkak.outbox.entity.Outbox;
import com.chalkak.outbox.repository.OutboxRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
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

    @Autowired
    private OutboxRepository outboxRepository;

    @AfterEach
    void cleanUp() {
        bidRequestResultRepository.deleteAll();
        outboxRepository.deleteAll();
    }

    @Test
    void 같은_멱등키로_다시_요청하면_같은_요청_ID를_반환하고_한_번만_저장한다() {
        UUID idempotencyKey = UUID.randomUUID();
        BidRequest bidRequest = new BidRequest(BigDecimal.valueOf(15_000));

        UUID firstRequestId = bidRequestService.request(1L, 1L, idempotencyKey, bidRequest);
        UUID retryRequestId = bidRequestService.request(1L, 1L, idempotencyKey, bidRequest);

        assertThat(retryRequestId).isEqualTo(firstRequestId);
        assertThat(outboxRepository.findAll()).extracting(Outbox::getRequestId).containsExactly(firstRequestId);
    }

    // 동시에 들어오면 둘 다 존재 조회를 통과할 수 있어, 마지막 방어선인 request_id 유니크 제약까지 확인한다
    @RepeatedTest(5)
    void 같은_멱등키로_동시에_요청하면_한_번만_저장하고_나머지는_처리_중_예외가_발생한다() throws InterruptedException {
        int requestCount = 10;
        UUID idempotencyKey = UUID.randomUUID();
        BidRequest bidRequest = new BidRequest(BigDecimal.valueOf(15_000));
        Set<UUID> requestIds = ConcurrentHashMap.newKeySet();
        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    requestIds.add(bidRequestService.request(1L, 1L, idempotencyKey, bidRequest));
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 조회에서 걸린 요청은 같은 요청 ID를 받고, 조회를 같이 통과한 요청은 유니크 제약에서 막혀 처리 중 예외가 난다
        assertThat(exceptions).allSatisfy(e -> assertThat(e)
            .isInstanceOfSatisfying(BusinessException.class,
                be -> assertThat(be.getErrorCode()).isEqualTo(BidErrorCode.BID_REQUEST_IN_PROGRESS)));
        assertThat(requestIds).hasSize(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void 이미_성공한_요청이_다시_전달돼_거절로_저장되면_PK_중복으로_실패하고_성공_결과가_유지된다() {
        BidMessage message = createMessage();
        bidRequestResultRepository.save(BidRequestResult.accepted(
            message.requestId(), message.auctionId(), message.bidderId(), message.bidAmount(), 1L));

        assertThatThrownBy(() -> bidRequestService.reject(message, AuctionErrorCode.BID_AMOUNT_TOO_LOW))
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
        return new BidMessage(UUID.randomUUID(), 1L, 1L, BigDecimal.valueOf(10_000), TimeUtils.now());
    }
}
