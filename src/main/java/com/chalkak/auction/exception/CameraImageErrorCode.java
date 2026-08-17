package com.chalkak.auction.exception;

import org.springframework.http.HttpStatus;

import com.chalkak.common.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CameraImageErrorCode implements ErrorCode {
    INVALID_CAMERA(HttpStatus.BAD_REQUEST, "CAMERA_IMAGE-001", "카메라 정보는 필수입니다."),
    INVALID_IMAGE_KEY(HttpStatus.BAD_REQUEST, "CAMERA_IMAGE-002", "이미지 경로는 필수입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
