package com.chalkak.bid.controller.response;

import com.chalkak.bid.entity.BidRequestStatus;

public enum BidResultStatus {
    ACCEPTED,
    REJECTED,
    PENDING;

    public static BidResultStatus from(BidRequestStatus status) {
        return switch (status) {
            case ACCEPTED -> ACCEPTED;
            case REJECTED -> REJECTED;
        };
    }
}
