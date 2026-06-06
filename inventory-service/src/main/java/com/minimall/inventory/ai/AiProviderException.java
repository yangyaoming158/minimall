package com.minimall.inventory.ai;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import java.util.Objects;

public class AiProviderException extends BusinessException {

    private final AiProviderType providerType;
    private final AiProviderErrorType providerErrorType;

    public AiProviderException(
            AiProviderType providerType,
            AiProviderErrorType providerErrorType,
            String message) {
        super(ErrorCode.INTERNAL_ERROR, normalizeMessage(message));
        this.providerType = Objects.requireNonNull(providerType, "providerType must not be null");
        this.providerErrorType = Objects.requireNonNull(providerErrorType, "providerErrorType must not be null");
    }

    public AiProviderException(
            AiProviderType providerType,
            AiProviderErrorType providerErrorType,
            String message,
            Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, normalizeMessage(message), cause);
        this.providerType = Objects.requireNonNull(providerType, "providerType must not be null");
        this.providerErrorType = Objects.requireNonNull(providerErrorType, "providerErrorType must not be null");
    }

    public AiProviderType getProviderType() {
        return providerType;
    }

    public AiProviderErrorType getProviderErrorType() {
        return providerErrorType;
    }

    private static String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "AI provider request failed";
        }
        return message.trim();
    }
}
