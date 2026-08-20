package com.chalkak.auction.exception;

import org.springframework.http.HttpStatus;

import com.chalkak.common.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CameraErrorCode implements ErrorCode {
    INVALID_BRAND(HttpStatus.BAD_REQUEST, "CAMERA-001", "브랜드는 필수입니다."),
    INVALID_MODEL_NAME(HttpStatus.BAD_REQUEST, "CAMERA-002", "모델명은 필수입니다."),
    INVALID_DESCRIPTION(HttpStatus.BAD_REQUEST, "CAMERA-003", "설명은 필수입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
