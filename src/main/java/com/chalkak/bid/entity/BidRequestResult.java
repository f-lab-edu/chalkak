package com.chalkak.bid.entity;

import com.chalkak.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "bid_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BidRequestResult extends BaseEntity {

    @Id
    UUID requestId;

    @Column(nullable = false)
    Long auctionId;

    @Column(nullable = false)
    Long bidderId;

    @Column(nullable = false)
    BigDecimal bidAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    BidRequestStatus status;

    // 거절 사유 (ErrorCode의 code), 성공이면 null
    String errorCode;

    // 성공해서 저장된 bids.id, 거절이면 null
    Long bidId;

    private BidRequestResult(UUID requestId, Long auctionId, Long bidderId, BigDecimal bidAmount,
        BidRequestStatus status, String errorCode, Long bidId) {
        this.requestId = requestId;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.bidAmount = bidAmount;
        this.status = status;
        this.errorCode = errorCode;
        this.bidId = bidId;
    }

    public static BidRequestResult accepted(UUID requestId, Long auctionId, Long bidderId, BigDecimal bidAmount, Long bidId) {
        return new BidRequestResult(requestId, auctionId, bidderId, bidAmount, BidRequestStatus.ACCEPTED, null, bidId);
    }

    public static BidRequestResult rejected(UUID requestId, Long auctionId, Long bidderId, BigDecimal bidAmount, String errorCode) {
        return new BidRequestResult(requestId, auctionId, bidderId, bidAmount, BidRequestStatus.REJECTED, errorCode,
            null);
    }
}
