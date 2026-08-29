package com.chalkak.bid.entity;

import com.chalkak.auction.entity.Auction;
import com.chalkak.bid.exception.BidErrorCode;
import com.chalkak.common.entity.BaseEntity;
import com.chalkak.common.exception.BusinessException;
import com.chalkak.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "bids")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Bid extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    Auction auction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bidder_id", nullable = false)
    User bidder;

    @Column(nullable = false)
    BigDecimal bidAmount;

    private Bid(Auction auction, User bidder, BigDecimal bidAmount) {
        validateBidAmount(bidAmount);

        this.auction = auction;
        this.bidder = bidder;
        this.bidAmount = bidAmount;
    }

    public static Bid submit(Auction auction, User bidder, BigDecimal bidAmount) {
        return new Bid(auction, bidder, bidAmount);
    }

    private static void validateBidAmount(BigDecimal bidAmount) {
        if (bidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BidErrorCode.INVALID_BID_AMOUNT);
        }
    }
}
