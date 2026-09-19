package com.chalkak.user.fixture;

import java.util.concurrent.atomic.AtomicInteger;

import com.chalkak.user.entity.User;

public class UserFixture {

    public static final String DEFAULT_EMAIL = "test@chalkak.com";
    public static final String DEFAULT_ENCODED_PASSWORD = "encoded-password";
    public static final String DEFAULT_RAW_PASSWORD = "raw-password";
    public static final String DEFAULT_PHONE = "010-1234-5678";
    public static final String DEFAULT_NICKNAME = "테스터";

    private static final AtomicInteger NICKNAME_SEQUENCE = new AtomicInteger();

    public static User create() {
        return create(DEFAULT_EMAIL, DEFAULT_ENCODED_PASSWORD, DEFAULT_PHONE, DEFAULT_NICKNAME);
    }

    public static User create(String email, String encodedPassword, String phone) {
        return create(email, encodedPassword, phone, nextNickname());
    }

    public static User create(String email, String encodedPassword, String phone, String nickname) {
        return User.signUp(email, encodedPassword, phone, nickname);
    }

    private static String nextNickname() {
        return "user" + NICKNAME_SEQUENCE.incrementAndGet();
    }
}
