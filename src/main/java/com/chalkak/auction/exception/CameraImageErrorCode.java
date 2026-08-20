package com.chalkak.auction.exception;

import org.springframework.http.HttpStatus;

import com.chalkak.common.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CameraImageErrorCode implements ErrorCode {
    INVALID_IMAGE_KEY(HttpStatus.BAD_REQUEST, "CAMERA_IMAGE-001", "이미지 경로는 필수입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
