package com.chalkak.point.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.common.exception.BusinessException;
import com.chalkak.point.exception.PointErrorCode;
import com.chalkak.point.fixture.PointFixture;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PointTest {

    @Test
    void 생성_시_가용_금액과_잠금_금액은_0이다() {
        Point point = PointFixture.create();

        assertThat(point.getAvailableAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(point.getLockedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void 충전하면_가용_금액이_증가한다() {
        Point point = PointFixture.create();
        point.charge(BigDecimal.valueOf(1_000));

        assertThat(point.getAvailableAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_000));
    }

    @Test
    void 충전_금액이_0이면_예외가_발생한다() {
        Point point = PointFixture.create();

        assertThatThrownBy(() -> point.charge(BigDecimal.ZERO))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", PointErrorCode.INVALID_CHARGE_AMOUNT);
    }

    @Test
    void 충전_금액이_음수이면_예외가_발생한다() {
        Point point = PointFixture.create();
        BigDecimal negativeAmount = BigDecimal.valueOf(-1_000);

        assertThatThrownBy(() -> point.charge(negativeAmount))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", PointErrorCode.INVALID_CHARGE_AMOUNT);
    }

    @Test
    void 잠그면_가용_금액이_줄고_잠금_금액이_늘어난다() {
        Point point = PointFixture.create();

        point.charge(BigDecimal.valueOf(1_000));
        point.lock(BigDecimal.valueOf(300));

        assertThat(point.getAvailableAmount()).isEqualByComparingTo(BigDecimal.valueOf(700));
        assertThat(point.getLockedAmount()).isEqualByComparingTo(BigDecimal.valueOf(300));
    }

    @Test
    void 잠금_금액이_0이하이면_예외가_발생한다() {
        Point point = PointFixture.create();

        point.charge(BigDecimal.valueOf(1_000));

        assertThatThrownBy(() -> point.lock(BigDecimal.ZERO))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", PointErrorCode.INVALID_LOCK_AMOUNT);

        BigDecimal negativeAmount = BigDecimal.valueOf(-1);
        assertThatThrownBy(() -> point.lock(negativeAmount))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", PointErrorCode.INVALID_LOCK_AMOUNT);
    }

    @Test
    void 잠금_금액이_가용_금액을_초과하면_예외가_발생한다() {
        Point point = PointFixture.create();

        point.charge(BigDecimal.valueOf(1_000));
        BigDecimal excessiveAmount = BigDecimal.valueOf(1_001);

        assertThatThrownBy(() -> point.lock(excessiveAmount))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", PointErrorCode.INSUFFICIENT_AVAILABLE_AMOUNT);
    }

    @Test
    void 잠금_해제하면_잠금_금액이_줄고_가용_금액이_늘어난다() {
        Point point = PointFixture.create();

        point.charge(BigDecimal.valueOf(1_000));
        point.lock(BigDecimal.valueOf(300));

        point.unlock(BigDecimal.valueOf(300));

        assertThat(point.getAvailableAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_000));
        assertThat(point.getLockedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void 잠금_해제_금액이_0이하이면_예외가_발생한다() {
        Point point = PointFixture.create();

        point.charge(BigDecimal.valueOf(1_000));
        point.lock(BigDecimal.valueOf(300));

        assertThatThrownBy(() -> point.unlock(BigDecimal.ZERO))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", PointErrorCode.INVALID_UNLOCK_AMOUNT);

        BigDecimal negativeAmount = BigDecimal.valueOf(-1);
        assertThatThrownBy(() -> point.unlock(negativeAmount))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", PointErrorCode.INVALID_UNLOCK_AMOUNT);
    }

    @Test
    void 잠금_해제_금액이_잠금_금액을_초과하면_예외가_발생한다() {
        Point point = PointFixture.create();

        point.charge(BigDecimal.valueOf(1_000));
        point.lock(BigDecimal.valueOf(300));
        BigDecimal excessiveAmount = BigDecimal.valueOf(301);

        assertThatThrownBy(() -> point.unlock(excessiveAmount))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", PointErrorCode.INSUFFICIENT_LOCKED_AMOUNT);
    }
}
