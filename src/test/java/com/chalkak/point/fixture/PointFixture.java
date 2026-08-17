package com.chalkak.point.fixture;

import com.chalkak.point.entity.Point;
import com.chalkak.user.entity.User;
import com.chalkak.user.fixture.UserFixture;

public class PointFixture {

    public static Point create() {
        return create(UserFixture.create());
    }

    public static Point create(User user) {
        return Point.open(user);
    }
}
