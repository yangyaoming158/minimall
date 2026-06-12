package com.minimall.inventory.ai.prompt;

import java.util.Objects;

public record AiPromptTemplate(
        AiPromptTemplateId id,
        String promptVersion,
        String outputSchemaVersion,
        String systemPrompt,
        String taskPrompt) {

    public AiPromptTemplate {
        Objects.requireNonNull(id, "id must not be null");
        promptVersion = requireText(promptVersion, "promptVersion");
        outputSchemaVersion = requireText(outputSchemaVersion, "outputSchemaVersion");
        systemPrompt = requireText(systemPrompt, "systemPrompt");
        taskPrompt = requireText(taskPrompt, "taskPrompt");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
