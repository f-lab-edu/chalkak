package com.chalkak.user.exception;

import com.chalkak.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
    INVALID_PHONE_FORMAT(HttpStatus.BAD_REQUEST, "USER-001", "전화번호 형식이 올바르지 않습니다."),
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "USER-002", "이메일 형식이 올바르지 않습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER-003", "이미 사용 중인 이메일입니다."),
    DUPLICATE_PHONE(HttpStatus.CONFLICT, "USER-004", "이미 사용 중인 전화번호입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
