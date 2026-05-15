package com.minimall.common.core.exception;

import com.minimall.common.core.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionMapsErrorCodeAndHttpStatus() {
        BusinessException exception = new BusinessException(ErrorCode.CONFLICT, "duplicate order");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        ApiResponse<Void> body = requireBody(response);
        assertFalse(body.isSuccess());
        assertEquals(ErrorCode.CONFLICT.getCode(), body.getCode());
        assertEquals("duplicate order", body.getMessage());
    }

    @Test
    void paymentBusinessExceptionsUseStableCodesAndConflictStatus() {
        assertBusinessException(ErrorCode.ORDER_CANCELLED, "order cancelled", "40901", HttpStatus.CONFLICT);
        assertBusinessException(ErrorCode.ORDER_INVALID_STATE, "order paid", "40902", HttpStatus.CONFLICT);
        assertBusinessException(ErrorCode.PAYMENT_ALREADY_SUCCESS, "already paid", "40903", HttpStatus.CONFLICT);
    }

    @Test
    void tooManyRequestsUsesStableCodeAndHttpStatus() {
        assertBusinessException(ErrorCode.TOO_MANY_REQUESTS, "rate limit exceeded", "42900",
                HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void methodArgumentNotValidExceptionMapsFieldErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new SampleRequest(), "sampleRequest");
        bindingResult.addError(new FieldError("sampleRequest", "quantity", "must be greater than 0"));
        MethodParameter parameter = new MethodParameter(
                SampleController.class.getDeclaredMethod("create", SampleRequest.class), 0);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentNotValidException(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiResponse<Void> body = requireBody(response);
        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), body.getCode());
        assertEquals("quantity: must be greater than 0", body.getMessage());
    }

    @Test
    void constraintViolationExceptionMapsViolations() {
        ConstraintViolationException exception = new ConstraintViolationException(
                Set.of(new SimpleConstraintViolation("userId", "must not be null")));

        ResponseEntity<ApiResponse<Void>> response = handler.handleConstraintViolationException(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiResponse<Void> body = requireBody(response);
        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), body.getCode());
        assertEquals("userId: must not be null", body.getMessage());
    }

    @Test
    void unexpectedExceptionMapsToInternalErrorWithoutLeakingMessage() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleException(new RuntimeException("database password"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ApiResponse<Void> body = requireBody(response);
        assertEquals(ErrorCode.INTERNAL_ERROR.getCode(), body.getCode());
        assertEquals(ErrorCode.INTERNAL_ERROR.getMessage(), body.getMessage());
    }

    private ApiResponse<Void> requireBody(ResponseEntity<ApiResponse<Void>> response) {
        ApiResponse<Void> body = response.getBody();
        assertNotNull(body);
        return body;
    }

    private void assertBusinessException(ErrorCode errorCode, String message, String expectedCode,
            HttpStatus expectedStatus) {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(
                new BusinessException(errorCode, message));

        assertEquals(expectedStatus, response.getStatusCode());
        ApiResponse<Void> body = requireBody(response);
        assertFalse(body.isSuccess());
        assertEquals(expectedCode, body.getCode());
        assertEquals(message, body.getMessage());
    }

    private record SampleRequest() {
    }

    private static final class SampleController {
        @SuppressWarnings("unused")
        void create(SampleRequest request) {
        }
    }

    private record SimpleConstraintViolation(String path, String message) implements ConstraintViolation<Object> {

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public String getMessageTemplate() {
            return message;
        }

        @Override
        public Object getRootBean() {
            return null;
        }

        @Override
        public Class<Object> getRootBeanClass() {
            return Object.class;
        }

        @Override
        public Object getLeafBean() {
            return null;
        }

        @Override
        public Object[] getExecutableParameters() {
            return new Object[0];
        }

        @Override
        public Object getExecutableReturnValue() {
            return null;
        }

        @Override
        public Path getPropertyPath() {
            return new SimplePath(path);
        }

        @Override
        public Object getInvalidValue() {
            return null;
        }

        @Override
        public ConstraintDescriptor<?> getConstraintDescriptor() {
            return null;
        }

        @Override
        public <U> U unwrap(Class<U> type) {
            throw new UnsupportedOperationException("unwrap is not supported");
        }
    }

    private record SimplePath(String name) implements Path {

        @Override
        public Iterator<Node> iterator() {
            return List.<Node>of(new SimplePathNode(name)).iterator();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private record SimplePathNode(String name) implements Path.Node {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isInIterable() {
            return false;
        }

        @Override
        public Integer getIndex() {
            return null;
        }

        @Override
        public Object getKey() {
            return null;
        }

        @Override
        public ElementKind getKind() {
            return ElementKind.PROPERTY;
        }

        @Override
        public <T extends Path.Node> T as(Class<T> nodeType) {
            return nodeType.cast(this);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
