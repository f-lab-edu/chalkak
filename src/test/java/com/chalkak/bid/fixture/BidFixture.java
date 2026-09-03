package com.chalkak.bid.fixture;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.fixture.AuctionFixture;
import com.chalkak.bid.entity.Bid;
import com.chalkak.user.entity.User;
import com.chalkak.user.fixture.UserFixture;
import java.math.BigDecimal;

public class BidFixture {

    public static final BigDecimal DEFAULT_BID_AMOUNT = BigDecimal.valueOf(20_000);

    public static Bid create() {
        return create(AuctionFixture.create(), UserFixture.create());
    }

    public static Bid create(Auction auction, User bidder) {
        return create(auction, bidder, DEFAULT_BID_AMOUNT);
    }

    public static Bid create(Auction auction, User bidder, BigDecimal bidAmount) {
        return Bid.submit(auction, bidder, bidAmount);
    }
}
