package com.chalkak.auction.exception;

import com.chalkak.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuctionErrorCode implements ErrorCode {
    INVALID_CAMERA(HttpStatus.BAD_REQUEST, "AUCTION-001", "카메라 정보는 필수입니다."),
    INVALID_START_PRICE(HttpStatus.BAD_REQUEST, "AUCTION-002", "시작가는 0보다 커야 합니다."),
    INVALID_CLOSES_AT(HttpStatus.BAD_REQUEST, "AUCTION-003", "마감 시각은 현재 시각 이후여야 합니다."),
    INVALID_IMAGE_COUNT(HttpStatus.BAD_REQUEST, "AUCTION-004", "이미지는 최소 3장 이상 등록해야 합니다."),
    OWNER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUCTION-005", "존재하지 않는 판매자입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
