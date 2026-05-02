package com.minimall.common.core.response;

import com.minimall.common.core.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseTest {

    @Test
    void successWithDataUsesSuccessCodeAndMessage() {
        ApiResponse<String> response = ApiResponse.success("ok");

        assertTrue(response.isSuccess());
        assertEquals(ErrorCode.SUCCESS.getCode(), response.getCode());
        assertEquals(ErrorCode.SUCCESS.getMessage(), response.getMessage());
        assertEquals("ok", response.getData());
    }

    @Test
    void successWithoutDataHasNullData() {
        ApiResponse<Object> response = ApiResponse.success();

        assertTrue(response.isSuccess());
        assertEquals(ErrorCode.SUCCESS.getCode(), response.getCode());
        assertNull(response.getData());
    }

    @Test
    void failureUsesErrorCodeAndCustomMessage() {
        ApiResponse<Object> response = ApiResponse.failure(ErrorCode.CONFLICT, "duplicate order");

        assertFalse(response.isSuccess());
        assertEquals(ErrorCode.CONFLICT.getCode(), response.getCode());
        assertEquals("duplicate order", response.getMessage());
        assertNull(response.getData());
    }
}
