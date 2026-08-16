package com.chalkak.auction.exception;

import org.springframework.http.HttpStatus;

import com.chalkak.common.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CameraErrorCode implements ErrorCode {
    INVALID_OWNER(HttpStatus.BAD_REQUEST, "CAMERA-001", "판매자 정보는 필수입니다."),
    INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "CAMERA-002", "카테고리는 필수입니다."),
    INVALID_BRAND(HttpStatus.BAD_REQUEST, "CAMERA-003", "브랜드는 필수입니다."),
    INVALID_MODEL_NAME(HttpStatus.BAD_REQUEST, "CAMERA-004", "모델명은 필수입니다."),
    INVALID_CONDITION_GRADE(HttpStatus.BAD_REQUEST, "CAMERA-005", "상태 등급은 필수입니다."),
    INVALID_DESCRIPTION(HttpStatus.BAD_REQUEST, "CAMERA-006", "설명은 필수입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
