package com.chalkak.bid.service;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.repository.AuctionRepository;
import com.chalkak.bid.controller.request.BidRequest;
import com.chalkak.bid.controller.response.BidResponse;
import com.chalkak.bid.entity.Bid;
import com.chalkak.bid.repository.BidRepository;
import com.chalkak.common.exception.BusinessException;
import com.chalkak.common.exception.CommonErrorCode;
import com.chalkak.point.service.PointService;
import com.chalkak.user.entity.User;
import com.chalkak.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final PointService pointService;
    private final UserRepository userRepository;

    @Transactional
    public BidResponse submit(Long auctionId, Long bidderId, BidRequest request) {
        BigDecimal bidAmount = request.bidAmount();

        // 큐(MessageGroupId)가 경매별 순서를 보장해도 락은 유지한다.
        // 마감 스케줄러가 같은 경매 행을 바꾸고, visibility timeout이 지나 재전달되면 같은 경매 메시지가 동시에 처리될 수 있다.
        Auction auction = auctionRepository.findByIdWithLock(auctionId).orElseThrow(() -> new BusinessException(
            CommonErrorCode.NOT_FOUND,
            CommonErrorCode.NOT_FOUND.formatted("경매")
        ));

        auction.validateAcceptingBids();
        auction.validateNotOwner(bidderId);

        User bidder = userRepository.findById(bidderId).orElseThrow(() -> new BusinessException(
            CommonErrorCode.NOT_FOUND,
            CommonErrorCode.NOT_FOUND.formatted("회원")
        ));

        Optional<Bid> beforeTopBid = bidRepository.findTopByAuctionIdOrderByBidAmountDesc(auctionId);

        auction.updateCurrentPrice(bidAmount);

        boolean isSameBidder = beforeTopBid
            .map(bid -> bid.getBidder().getId().equals(bidderId))
            .orElse(false);

        if (isSameBidder) {
            BigDecimal additionalAmount = bidAmount.subtract(beforeTopBid.get().getBidAmount());
            pointService.lock(bidderId, additionalAmount);
        } else {
            if (beforeTopBid.isPresent()) {
                Bid previousBid = beforeTopBid.get();
                Long previousBidderId = previousBid.getBidder().getId();
                pointService.settlePoint(bidderId, previousBidderId, bidAmount, previousBid.getBidAmount());
            } else {
                pointService.lock(bidderId, bidAmount);
            }

        }

        auction.updateExtendCloseAt();

        Bid bidLog = Bid.submit(auction, bidder, bidAmount);
        Bid bid = bidRepository.save(bidLog);

        return BidResponse.from(bid);
    }
}
