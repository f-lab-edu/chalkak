package com.chalkak.user.fixture;

import java.util.concurrent.atomic.AtomicInteger;

import com.chalkak.user.controller.request.UserRequest;

public class UserRequestFixture {

    public static final String DEFAULT_EMAIL = "test@chalkak.com";
    public static final String DEFAULT_PASSWORD = "raw-password";
    public static final String DEFAULT_PHONE = "010-1234-5678";
    public static final String DEFAULT_NICKNAME = "테스터";

    private static final AtomicInteger NICKNAME_SEQUENCE = new AtomicInteger();

    public static UserRequest create() {
        return create(DEFAULT_EMAIL, DEFAULT_PASSWORD, DEFAULT_PHONE, DEFAULT_NICKNAME);
    }

    public static UserRequest create(String email, String password, String phone) {
        return create(email, password, phone, nextNickname());
    }

    public static UserRequest create(String email, String password, String phone, String nickname) {
        return new UserRequest(email, password, phone, nickname);
    }

    private static String nextNickname() {
        return "user" + NICKNAME_SEQUENCE.incrementAndGet();
    }
}
