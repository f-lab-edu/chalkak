package com.chalkak.common.exception;

import java.util.List;

import org.springframework.validation.BindingResult;

public record ErrorResponse(
    String code,
    String message,
    List<FieldError> errors
) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getCode(), message, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, BindingResult bindingResult) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), FieldError.of(bindingResult));
    }

    private record FieldError(
        String field,
        String value,
        String reason
    ) {
        private static List<FieldError> of(BindingResult bindingResult) {
            return bindingResult.getFieldErrors().stream()
                .map(error -> new FieldError(
                    error.getField(),
                    error.getRejectedValue() == null ? "" : error.getRejectedValue().toString(),
                    error.getDefaultMessage()
                ))
                .toList();
        }
    }
}
