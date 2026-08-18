package com.chalkak.user.fixture;

import com.chalkak.user.entity.User;

public class UserFixture {

    public static final String DEFAULT_EMAIL = "test@chalkak.com";
    public static final String DEFAULT_ENCODED_PASSWORD = "encoded-password";
    public static final String DEFAULT_RAW_PASSWORD = "raw-password";
    public static final String DEFAULT_PHONE = "010-1234-5678";

    public static User create() {
        return create(DEFAULT_EMAIL, DEFAULT_ENCODED_PASSWORD, DEFAULT_PHONE);
    }

    public static User create(String email, String encodedPassword, String phone) {
        return User.signUp(email, encodedPassword, phone);
    }
}
