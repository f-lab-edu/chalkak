package com.chalkak.auction.exception;

import com.chalkak.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuctionErrorCode implements ErrorCode {
    INVALID_START_PRICE(HttpStatus.BAD_REQUEST, "AUCTION-001", "시작가는 0보다 커야 합니다."),
    INVALID_CLOSES_AT(HttpStatus.BAD_REQUEST, "AUCTION-002", "마감 시각은 현재 시각 이후여야 합니다."),
    INVALID_IMAGE_COUNT(HttpStatus.BAD_REQUEST, "AUCTION-003", "이미지는 최소 3장 이상 등록해야 합니다."),
    BID_AMOUNT_TOO_LOW(HttpStatus.BAD_REQUEST, "AUCTION-004", "현재가가 갱신되어 입찰가가 더 이상 유효하지 않습니다."),
    AUCTION_ALREADY_CLOSED(HttpStatus.BAD_REQUEST, "AUCTION-005", "입찰이 마감되었거나 취소된 경매입니다."),
    SELF_BID_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "AUCTION-006", "본인이 등록한 경매에는 입찰할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
