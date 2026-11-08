package com.chalkak.bid.service;

import com.chalkak.auction.controller.response.AuctionStatusResponse;
import com.chalkak.auction.service.AuctionService;
import com.chalkak.bid.controller.request.BidRequest;
import com.chalkak.bid.controller.response.BidResponse;
import com.chalkak.bid.controller.response.BidResultResponse;
import com.chalkak.bid.entity.BidRequestResult;
import com.chalkak.bid.event.BidAcceptedEvent;
import com.chalkak.bid.event.BidRejectedEvent;
import com.chalkak.bid.exception.BidErrorCode;
import com.chalkak.bid.queue.BidMessage;
import com.chalkak.bid.repository.BidRequestResultRepository;
import com.chalkak.common.exception.BusinessException;
import com.chalkak.common.exception.ErrorCode;
import com.chalkak.common.util.JsonConverter;
import com.chalkak.common.util.TimeUtils;
import com.chalkak.outbox.entity.Outbox;
import com.chalkak.outbox.repository.OutboxRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class BidRequestService {

    private final BidService bidService;
    private final AuctionService auctionService;
    private final BidRequestResultRepository bidRequestResultRepository;
    private final OutboxRepository outboxRepository;
    private final JsonConverter jsonConverter;
    private final ApplicationEventPublisher eventPublisher;

    public UUID request(Long auctionId, Long bidderId, UUID idempotencyKey, BidRequest bidRequest) {
        UUID requestId = generateRequestId(bidderId, idempotencyKey);
        if (outboxRepository.existsByRequestId(requestId)) {
            return requestId;
        }
        BidMessage message = new BidMessage(requestId, auctionId, bidderId, bidRequest.bidAmount(), TimeUtils.now());
        try {
            outboxRepository.save(Outbox.pending(requestId, auctionId, bidderId, bidRequest.bidAmount(), jsonConverter.toJson(message)));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(BidErrorCode.BID_REQUEST_IN_PROGRESS);
        }
        return requestId;
    }

    private UUID generateRequestId(Long bidderId, UUID idempotencyKey) {
        return UUID.nameUUIDFromBytes((bidderId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
    }

    @Transactional(readOnly = true)
    public BidResultResponse getResult(UUID requestId, Long userId) {
        return bidRequestResultRepository.findById(requestId)
            .filter(result -> result.getBidderId().equals(userId))
            .map(BidResultResponse::from)
            .orElseGet(() -> BidResultResponse.pending(requestId));
    }

    public boolean exists(UUID requestId) {
        return bidRequestResultRepository.existsById(requestId);
    }

    public void accept(BidMessage message) {
        BidResponse bid = bidService.submit(message.auctionId(), message.bidderId(), message.requestedAt(), new BidRequest(message.bidAmount()));
        bidRequestResultRepository.save(BidRequestResult.accepted(
            message.requestId(), message.auctionId(), message.bidderId(), message.bidAmount(), bid.id()));
        AuctionStatusResponse status = auctionService.getStatus(message.auctionId());
        eventPublisher.publishEvent(new BidAcceptedEvent(message.requestId(), message.auctionId(), message.bidderId(), status));
    }

    public void rejectUnpublished(Outbox outbox) {
        outbox.markFailed();
        outboxRepository.save(outbox);
        reject(new BidMessage(outbox.getRequestId(), outbox.getAuctionId(), outbox.getBidderId(),
            outbox.getBidAmount(), outbox.getCreatedAt()), BidErrorCode.BID_NOT_PROCESSED);
    }

    public void reject(BidMessage message, ErrorCode errorCode) {
        String code = errorCode.getCode();
        bidRequestResultRepository.save(BidRequestResult.rejected(
            message.requestId(), message.auctionId(), message.bidderId(), message.bidAmount(), code));
        eventPublisher.publishEvent(new BidRejectedEvent(message.requestId(), message.auctionId(), message.bidderId(), code));
    }
}
