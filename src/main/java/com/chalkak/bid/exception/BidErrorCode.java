package com.chalkak.bid.exception;

import com.chalkak.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BidErrorCode implements ErrorCode {
    INVALID_BID_AMOUNT(HttpStatus.BAD_REQUEST, "BID-001", "입찰가는 0보다 커야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
