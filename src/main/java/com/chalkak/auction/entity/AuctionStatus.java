package com.chalkak.auction.entity;

public enum AuctionStatus {
    IN_PROGRESS(true),
    SUCCESSFUL(false),
    FAILED(false),
    CANCELLED(false);

    private final boolean acceptingBids;

    AuctionStatus(boolean acceptingBids) {
        this.acceptingBids = acceptingBids;
    }

    public boolean isAcceptingBids() {
        return acceptingBids;
    }
}
