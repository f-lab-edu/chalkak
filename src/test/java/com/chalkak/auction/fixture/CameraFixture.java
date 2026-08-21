package com.chalkak.auction.fixture;

import com.chalkak.auction.entity.Camera;
import com.chalkak.auction.entity.CameraCategory;
import com.chalkak.auction.entity.CameraConditionGrade;
import com.chalkak.user.entity.User;
import com.chalkak.user.fixture.UserFixture;

public class CameraFixture {

    public static final CameraCategory DEFAULT_CATEGORY = CameraCategory.MIRRORLESS;
    public static final String DEFAULT_BRAND = "Canon";
    public static final String DEFAULT_MODEL_NAME = "EOS R5";
    public static final CameraConditionGrade DEFAULT_CONDITION_GRADE = CameraConditionGrade.A;
    public static final String DEFAULT_DESCRIPTION = "상태 좋은 카메라입니다.";

    public static Camera create() {
        return create(UserFixture.create());
    }

    public static Camera create(User owner) {
        return create(owner, DEFAULT_CATEGORY, DEFAULT_BRAND, DEFAULT_MODEL_NAME, DEFAULT_CONDITION_GRADE, DEFAULT_DESCRIPTION);
    }

    public static Camera create(User owner, CameraCategory category, String brand, String modelName,
            CameraConditionGrade conditionGrade, String description) {
        return Camera.register(owner, category, brand, modelName, conditionGrade, description);
    }
}
