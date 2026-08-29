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

        // 현재가 검증은 Auction.updateCurrentPrice(), 포인트 부족 검증은 Point.lock() 내부에서 처리됨
        auction.updateCurrentPrice(bidAmount);
        pointService.lock(bidderId, bidAmount);

        auction.updateExtendCloseAt();

        // 이전 최고 입찰자 포인트 잠금 해제 (첫 입찰이면 없을 수 있음)
        bidRepository.findTopByAuctionIdOrderByBidAmountDesc(auctionId)
            .ifPresent(beforeBestBidLog -> pointService.unlock(
                beforeBestBidLog.getBidder().getId(), beforeBestBidLog.getBidAmount()));

        Bid bidLog = Bid.submit(auction, bidder, bidAmount);
        Bid bid = bidRepository.save(bidLog);

        return BidResponse.from(bid);
    }
}
