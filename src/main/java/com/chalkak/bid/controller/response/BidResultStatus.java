package com.chalkak.bid.controller.response;

import com.chalkak.bid.entity.BidRequestStatus;

// PENDING은 아직 결과가 저장되지 않은 요청에만 쓰는 응답 상태라 엔티티의 BidRequestStatus와 분리한다
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
