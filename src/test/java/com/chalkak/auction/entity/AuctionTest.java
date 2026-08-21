package com.chalkak.auction.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.auction.exception.AuctionErrorCode;
import com.chalkak.auction.fixture.AuctionFixture;
import com.chalkak.auction.fixture.CameraFixture;
import com.chalkak.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AuctionTest {

    @Test
    void 유효한_값이면_Auction이_생성되고_현재가는_시작가와_같다() {
        Auction auction = AuctionFixture.create();

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.IN_PROGRESS);
        assertThat(auction.getCurrentPrice()).isEqualByComparingTo(AuctionFixture.DEFAULT_START_PRICE);
        assertThat(auction.getExtendedClosesAt()).isEqualTo(auction.getClosesAt());
    }

    @Test
    void startPrice가_0이하이면_예외가_발생한다() {
        Camera camera = CameraFixture.create();
        LocalDateTime closesAt = LocalDateTime.now().plusDays(3);

        assertThatThrownBy(() -> AuctionFixture.create(camera, BigDecimal.ZERO, closesAt))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", AuctionErrorCode.INVALID_START_PRICE);
    }

    @Test
    void closesAt이_현재_시각_이전이면_예외가_발생한다() {
        Camera camera = CameraFixture.create();
        LocalDateTime pastClosesAt = LocalDateTime.now().minusDays(1);

        assertThatThrownBy(() -> AuctionFixture.create(camera, AuctionFixture.DEFAULT_START_PRICE, pastClosesAt))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", AuctionErrorCode.INVALID_CLOSES_AT);
    }
}
