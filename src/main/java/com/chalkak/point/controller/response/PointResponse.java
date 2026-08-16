package com.chalkak.point.controller.response;

import java.math.BigDecimal;

import com.chalkak.point.entity.Point;

public record PointResponse(
    Long userId,
    BigDecimal availableAmount,
    BigDecimal lockedAmount
) {
    public static PointResponse from(Point point) {
        return new PointResponse(point.getUser().getId(), point.getAvailableAmount(), point.getLockedAmount());
    }
}
