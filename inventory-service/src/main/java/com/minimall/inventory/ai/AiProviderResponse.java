package com.minimall.inventory.ai;

import java.util.Objects;

public record AiProviderResponse(
        AiProviderType provider,
        String model,
        String content,
        AiProviderTokenUsage tokenUsage,
        String requestId) {

    public AiProviderResponse {
        Objects.requireNonNull(provider, "provider must not be null");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        model = normalizeOptional(model);
        tokenUsage = tokenUsage == null ? AiProviderTokenUsage.empty() : tokenUsage;
        requestId = normalizeOptional(requestId);
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
