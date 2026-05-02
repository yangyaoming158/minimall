package com.minimall.common.core.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class BusinessExceptionTest {

    @Test
    void constructorWithErrorCodeUsesDefaultMessage() {
        BusinessException exception = new BusinessException(ErrorCode.NOT_FOUND);

        assertSame(ErrorCode.NOT_FOUND, exception.getErrorCode());
        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        assertEquals(ErrorCode.NOT_FOUND.getMessage(), exception.getMessage());
    }

    @Test
    void constructorWithCustomMessageKeepsErrorCode() {
        BusinessException exception = new BusinessException(ErrorCode.BAD_REQUEST, "invalid quantity");

        assertSame(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
        assertEquals("invalid quantity", exception.getMessage());
    }

    @Test
    void constructorWithCauseKeepsCause() {
        IllegalStateException cause = new IllegalStateException("boom");
        BusinessException exception = new BusinessException(ErrorCode.INTERNAL_ERROR, "failed", cause);

        assertSame(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertSame(cause, exception.getCause());
        assertEquals("failed", exception.getMessage());
    }
}
