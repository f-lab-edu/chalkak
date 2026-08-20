package com.chalkak.auction.entity;

import com.chalkak.auction.exception.AuctionErrorCode;
import com.chalkak.common.entity.BaseEntity;
import com.chalkak.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "auctions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Auction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camera_id", nullable = false)
    Camera camera;

    @Column(name = "start_price", nullable = false)
    BigDecimal startPrice;

    @Column(name = "current_price", nullable = false)
    BigDecimal currentPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    AuctionStatus status;

    @Column(name = "closes_at", nullable = false)
    LocalDateTime closesAt;

    @Column(name = "extended_closes_at", nullable = false)
    LocalDateTime extendedClosesAt;

    private Auction(Camera camera, BigDecimal startPrice, LocalDateTime closesAt) {
        validateCamera(camera);
        validateStartPrice(startPrice);
        validateClosesAt(closesAt);

        this.camera = camera;
        this.startPrice = startPrice;
        this.currentPrice = startPrice;
        this.status = AuctionStatus.IN_PROGRESS;
        this.closesAt = closesAt;
        this.extendedClosesAt = closesAt;
    }

    public static Auction start(Camera camera, BigDecimal startPrice, LocalDateTime closesAt) {
        return new Auction(camera, startPrice, closesAt);
    }

    private static void validateCamera(Camera camera) {
        if (camera == null) {
            throw new BusinessException(AuctionErrorCode.INVALID_CAMERA);
        }
    }

    private static void validateStartPrice(BigDecimal startPrice) {
        if (startPrice == null || startPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(AuctionErrorCode.INVALID_START_PRICE);
        }
    }

    private static void validateClosesAt(LocalDateTime closesAt) {
        if (closesAt == null || !closesAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(AuctionErrorCode.INVALID_CLOSES_AT);
        }
    }
}
