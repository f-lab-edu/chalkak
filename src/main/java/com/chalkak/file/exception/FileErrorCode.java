package com.chalkak.file.exception;

import com.chalkak.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FileErrorCode implements ErrorCode {
    UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE-001", "파일 업로드에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
