package com.chalkak.bid.controller.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record BidRequest(
    @NotNull(message = "입찰 금액은 필수입니다.")
    @Positive(message = "입찰 금액은 0보다 커야 합니다.")
    @Digits(integer = 9, fraction = 0, message = "입찰 금액은 소수점 없는 정수(원)여야 합니다.")
    BigDecimal bidAmount
) {
}
