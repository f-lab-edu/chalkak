package com.chalkak.bid.exception;

import com.chalkak.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BidErrorCode implements ErrorCode {
    INVALID_BID_AMOUNT(HttpStatus.BAD_REQUEST, "BID-001", "입찰가는 0보다 커야 합니다."),
    // 큐에서 입찰을 처리하지 못해 DLQ로 보낸 경우 (재시도 불가 예외 또는 재시도 횟수 소진)
    BID_NOT_PROCESSED(HttpStatus.SERVICE_UNAVAILABLE, "BID-002", "입찰이 반영되지 않았습니다. 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
