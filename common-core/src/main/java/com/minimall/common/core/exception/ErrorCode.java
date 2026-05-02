package com.minimall.common.core.exception;

public enum ErrorCode {

    SUCCESS("0", "success"),
    BAD_REQUEST("40000", "Bad request"),
    VALIDATION_ERROR("40001", "Validation failed"),
    UNAUTHORIZED("40100", "Unauthorized"),
    FORBIDDEN("40300", "Forbidden"),
    NOT_FOUND("40400", "Resource not found"),
    CONFLICT("40900", "Conflict"),
    INTERNAL_ERROR("50000", "Internal server error");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
