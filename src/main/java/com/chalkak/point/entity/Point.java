package com.chalkak.point.entity;

import com.chalkak.common.entity.BaseEntity;
import com.chalkak.common.exception.BusinessException;
import com.chalkak.point.exception.PointErrorCode;
import com.chalkak.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "points")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Point extends BaseEntity {

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

    private Point(User user) {
        this.user = user;
        this.availableAmount = BigDecimal.ZERO;
        this.lockedAmount = BigDecimal.ZERO;
    }

    public static Point open(User user) {
        return new Point(user);
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
