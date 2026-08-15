package com.chalkak.user.fixture;

import com.chalkak.user.controller.request.UserRequest;

public class UserRequestFixture {

    public static final String DEFAULT_EMAIL = "test@chalkak.com";
    public static final String DEFAULT_PASSWORD = "raw-password";
    public static final String DEFAULT_PHONE = "010-1234-5678";

    public static UserRequest create() {
        return create(DEFAULT_EMAIL, DEFAULT_PASSWORD, DEFAULT_PHONE);
    }

    public static UserRequest create(String email, String password, String phone) {
        return new UserRequest(email, password, phone);
    }
}
