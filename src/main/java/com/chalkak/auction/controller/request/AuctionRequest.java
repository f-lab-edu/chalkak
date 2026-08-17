package com.chalkak.auction.controller.request;

import com.chalkak.auction.entity.CameraCategory;
import com.chalkak.auction.entity.CameraConditionGrade;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuctionRequest(
    @NotNull
    CameraCategory category,

    @NotBlank
    String brand,

    @NotBlank
    String modelName,

    @NotNull
    CameraConditionGrade conditionGrade,

    @NotBlank
    String description,

    @NotNull
    @Positive
    BigDecimal startPrice,

    @NotNull
    @Future
    LocalDateTime closesAt
) {
}
