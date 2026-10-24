package com.chalkak.bid.service;

import com.chalkak.auction.controller.response.AuctionStatusResponse;
import com.chalkak.auction.service.AuctionService;
import com.chalkak.bid.controller.request.BidRequest;
import com.chalkak.bid.controller.response.BidResponse;
import com.chalkak.bid.controller.response.BidResultResponse;
import com.chalkak.bid.entity.BidRequestResult;
import com.chalkak.bid.event.BidAcceptedEvent;
import com.chalkak.bid.event.BidRejectedEvent;
import com.chalkak.bid.queue.BidMessage;
import com.chalkak.bid.queue.BidMessageProducer;
import com.chalkak.bid.repository.BidRequestResultRepository;
import com.chalkak.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class BidRequestService {

    private final BidService bidService;
    private final AuctionService auctionService;
    private final BidRequestResultRepository bidRequestResultRepository;
    private final BidMessageProducer producer;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UUID request(Long auctionId, Long bidderId, UUID idempotencyKey, BidRequest bidRequest) {
        UUID requestId = generateRequestId(bidderId, idempotencyKey);
        producer.send(new BidMessage(requestId, auctionId, bidderId, bidRequest.bidAmount()));
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
        BidResponse bid = bidService.submit(message.auctionId(), message.bidderId(), new BidRequest(message.bidAmount()));
        bidRequestResultRepository.save(BidRequestResult.accepted(
            message.requestId(), message.auctionId(), message.bidderId(), message.bidAmount(), bid.id()));
        AuctionStatusResponse status = auctionService.getStatus(message.auctionId());
        eventPublisher.publishEvent(new BidAcceptedEvent(message.requestId(), message.auctionId(), message.bidderId(), status));
    }

    public void reject(BidMessage message, BusinessException e) {
        String errorCode = e.getErrorCode().getCode();
        bidRequestResultRepository.save(BidRequestResult.rejected(
            message.requestId(), message.auctionId(), message.bidderId(), message.bidAmount(), errorCode));
        eventPublisher.publishEvent(new BidRejectedEvent(message.requestId(), message.auctionId(), message.bidderId(), errorCode));
    }
}
