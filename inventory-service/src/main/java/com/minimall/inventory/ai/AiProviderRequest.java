package com.minimall.inventory.ai;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AiProviderRequest(
        String promptVersion,
        String outputSchemaVersion,
        List<AiProviderMessage> messages,
        Map<String, Object> input) {

    public AiProviderRequest {
        promptVersion = requireText(promptVersion, "promptVersion");
        outputSchemaVersion = requireText(outputSchemaVersion, "outputSchemaVersion");
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        messages = List.copyOf(messages);
        input = input == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(input));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
