package com.chalkak.point.exception;

import com.chalkak.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PointErrorCode implements ErrorCode {
    INVALID_CHARGE_AMOUNT(HttpStatus.BAD_REQUEST, "POINT-001", "충전 금액은 0보다 커야 합니다."),
    INVALID_LOCK_AMOUNT(HttpStatus.BAD_REQUEST, "POINT-002", "잠금 금액은 0보다 커야 합니다."),
    INSUFFICIENT_AVAILABLE_AMOUNT(HttpStatus.BAD_REQUEST, "POINT-003", "가용 금액이 부족합니다."),
    INVALID_UNLOCK_AMOUNT(HttpStatus.BAD_REQUEST, "POINT-004", "잠금 해제 금액은 0보다 커야 합니다."),
    INSUFFICIENT_LOCKED_AMOUNT(HttpStatus.BAD_REQUEST, "POINT-005", "잠긴 금액이 부족합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
