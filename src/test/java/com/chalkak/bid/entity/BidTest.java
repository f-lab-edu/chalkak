package com.chalkak.bid.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.fixture.AuctionFixture;
import com.chalkak.bid.exception.BidErrorCode;
import com.chalkak.bid.fixture.BidFixture;
import com.chalkak.common.exception.BusinessException;
import com.chalkak.user.entity.User;
import com.chalkak.user.fixture.UserFixture;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BidTest {

    @Test
    void 유효한_값이면_Bid가_생성된다() {
        Bid bid = BidFixture.create();

        assertThat(bid.getBidAmount()).isEqualByComparingTo(BidFixture.DEFAULT_BID_AMOUNT);
    }

    @Test
    void bidAmount가_0이하이면_예외가_발생한다() {
        Auction auction = AuctionFixture.create();
        User bidder = UserFixture.create();

        assertThatThrownBy(() -> BidFixture.create(auction, bidder, BigDecimal.ZERO))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", BidErrorCode.INVALID_BID_AMOUNT);
    }
}
