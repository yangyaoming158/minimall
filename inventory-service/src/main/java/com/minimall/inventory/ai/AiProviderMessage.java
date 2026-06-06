package com.minimall.inventory.ai;

import java.util.Objects;

public record AiProviderMessage(AiProviderMessageRole role, String content) {

    public AiProviderMessage {
        Objects.requireNonNull(role, "role must not be null");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }

    public static AiProviderMessage system(String content) {
        return new AiProviderMessage(AiProviderMessageRole.SYSTEM, content);
    }

    public static AiProviderMessage user(String content) {
        return new AiProviderMessage(AiProviderMessageRole.USER, content);
    }

    public static AiProviderMessage assistant(String content) {
        return new AiProviderMessage(AiProviderMessageRole.ASSISTANT, content);
    }
}
