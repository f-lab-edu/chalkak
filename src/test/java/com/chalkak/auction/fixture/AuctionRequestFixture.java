package com.chalkak.auction.fixture;

import com.chalkak.auction.controller.request.AuctionRequest;
import com.chalkak.auction.entity.CameraCategory;
import com.chalkak.auction.entity.CameraConditionGrade;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AuctionRequestFixture {

    public static final CameraCategory DEFAULT_CATEGORY = CameraCategory.MIRRORLESS;
    public static final String DEFAULT_BRAND = "Canon";
    public static final String DEFAULT_MODEL_NAME = "EOS R5";
    public static final CameraConditionGrade DEFAULT_CONDITION_GRADE = CameraConditionGrade.A;
    public static final String DEFAULT_DESCRIPTION = "상태 좋은 카메라입니다.";
    public static final BigDecimal DEFAULT_START_PRICE = BigDecimal.valueOf(10_000);

    public static AuctionRequest create() {
        return create(DEFAULT_START_PRICE);
    }

    public static AuctionRequest create(BigDecimal startPrice) {
        return create(DEFAULT_CATEGORY, DEFAULT_BRAND, DEFAULT_MODEL_NAME, DEFAULT_CONDITION_GRADE,
            DEFAULT_DESCRIPTION, startPrice, LocalDateTime.now().plusDays(3));
    }

    public static AuctionRequest create(CameraCategory category, String brand, String modelName,
            CameraConditionGrade conditionGrade, String description, BigDecimal startPrice, LocalDateTime closesAt) {
        return new AuctionRequest(category, brand, modelName, conditionGrade, description, startPrice, closesAt);
    }
}
