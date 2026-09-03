package com.chalkak.bid.repository;

import com.chalkak.bid.entity.Bid;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {
    Optional<Bid> findTopByAuctionIdOrderByBidAmountDesc(Long auctionId);
}
