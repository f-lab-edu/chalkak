package com.chalkak.bid.service;

import com.chalkak.auction.controller.response.AuctionStatusResponse;
import com.chalkak.auction.service.AuctionService;
import com.chalkak.bid.controller.request.BidRequest;
import com.chalkak.bid.controller.response.BidResponse;
import com.chalkak.bid.controller.response.BidResultResponse;
import com.chalkak.bid.entity.BidRequestResult;
import com.chalkak.bid.queue.BidMessage;
import com.chalkak.bid.queue.BidMessageProducer;
import com.chalkak.bid.repository.BidRequestResultRepository;
import com.chalkak.common.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

    // DB를 쓰지 않으므로 트랜잭션에서 뺀다. 트랜잭션이 시작되면 SQS 전송이 끝날 때까지 DB 커넥션을 붙잡는다
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UUID request(Long auctionId, Long bidderId, BidRequest bidRequest) {
        UUID requestId = UUID.randomUUID();
        producer.send(new BidMessage(requestId, auctionId, bidderId, bidRequest));
        return requestId;
    }

    // 결과가 아직 없거나 다른 사람의 요청이면 PENDING으로 응답해서, 다른 사람의 요청이 존재한다는 사실도 드러내지 않는다
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

    public AuctionStatusResponse accept(BidMessage message) {
        BidResponse bid = bidService.submit(message.auctionId(), message.bidderId(), message.bidRequest());
        bidRequestResultRepository.save(BidRequestResult.accepted(
            message.requestId(), message.auctionId(), message.bidderId(), message.bidRequest().bidAmount(), bid.id()));
        return auctionService.getStatus(message.auctionId());
    }

    public void reject(BidMessage message, BusinessException e) {
        bidRequestResultRepository.save(BidRequestResult.rejected(
            message.requestId(), message.auctionId(), message.bidderId(), message.bidRequest().bidAmount(),
            e.getErrorCode().getCode()));
    }
}
