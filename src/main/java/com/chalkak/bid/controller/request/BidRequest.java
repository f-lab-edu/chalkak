package com.chalkak.bid.controller.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record BidRequest(
    @NotNull(message = "입찰 금액은 필수입니다.")
    @Positive(message = "입찰 금액은 0보다 커야 합니다.")
    BigDecimal bidAmount
) {
}
