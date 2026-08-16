package com.chalkak.point.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import com.chalkak.common.exception.BusinessException;
import com.chalkak.point.exception.PointErrorCode;
import com.chalkak.user.entity.User;

@Entity
@Table(name = "points")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class Point {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    User user;

    @Column(nullable = false)
    BigDecimal availableAmount;

    @Column(nullable = false)
    BigDecimal lockedAmount;

    @CreatedDate
    @Column(updatable = false)
    LocalDateTime createdAt;

    @LastModifiedDate
    LocalDateTime updatedAt;

    @Builder
    public Point(User user) {
        this.user = user;
        this.availableAmount = BigDecimal.ZERO;
        this.lockedAmount = BigDecimal.ZERO;
    }

    public void charge(BigDecimal amount) {
        validateChargeAmount(amount);
        this.availableAmount = this.availableAmount.add(amount);
    }

    public void lock(BigDecimal amount) {
        validateLockAmount(amount);
        this.availableAmount = this.availableAmount.subtract(amount);
        this.lockedAmount = this.lockedAmount.add(amount);
    }

    public void unlock(BigDecimal amount) {
        validateUnlockAmount(amount);
        this.lockedAmount = this.lockedAmount.subtract(amount);
        this.availableAmount = this.availableAmount.add(amount);
    }

    private static void validateChargeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(PointErrorCode.INVALID_CHARGE_AMOUNT);
        }
    }

    private void validateLockAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(PointErrorCode.INVALID_LOCK_AMOUNT);
        }
        if (amount.compareTo(this.availableAmount) > 0) {
            throw new BusinessException(PointErrorCode.INSUFFICIENT_AVAILABLE_AMOUNT);
        }
    }

    private void validateUnlockAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(PointErrorCode.INVALID_UNLOCK_AMOUNT);
        }
        if (amount.compareTo(this.lockedAmount) > 0) {
            throw new BusinessException(PointErrorCode.INSUFFICIENT_LOCKED_AMOUNT);
        }
    }
}
