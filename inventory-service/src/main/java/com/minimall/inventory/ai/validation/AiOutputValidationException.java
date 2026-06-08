package com.minimall.inventory.ai.validation;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;

public class AiOutputValidationException extends BusinessException {

    public AiOutputValidationException(String reason) {
        super(ErrorCode.VALIDATION_ERROR, "AI output validation failed: " + reason);
    }
}
