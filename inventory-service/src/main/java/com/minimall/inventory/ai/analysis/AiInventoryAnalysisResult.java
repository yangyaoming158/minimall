package com.minimall.inventory.ai.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.minimall.inventory.ai.AiProviderTokenUsage;
import com.minimall.inventory.ai.AiProviderType;
import com.minimall.inventory.ai.prompt.AiPromptTemplateId;
import com.minimall.inventory.ai.validation.AiAnalysisType;
import com.minimall.inventory.domain.AiSuggestionValidationStatus;
import java.util.Objects;

public record AiInventoryAnalysisResult(
        AiPromptTemplateId templateId,
        AiAnalysisType analysisType,
        String promptVersion,
        String outputSchemaVersion,
        AiProviderType provider,
        String model,
        String providerRequestId,
        AiProviderTokenUsage tokenUsage,
        AiSuggestionValidationStatus validationStatus,
        String inputSnapshotJson,
        String rawModelOutputJson,
        String validatedOutputJson,
        JsonNode output) {

    public AiInventoryAnalysisResult {
        Objects.requireNonNull(templateId, "templateId must not be null");
        Objects.requireNonNull(analysisType, "analysisType must not be null");
        promptVersion = requireText(promptVersion, "promptVersion");
        outputSchemaVersion = requireText(outputSchemaVersion, "outputSchemaVersion");
        Objects.requireNonNull(provider, "provider must not be null");
        tokenUsage = tokenUsage == null ? AiProviderTokenUsage.empty() : tokenUsage;
        validationStatus = validationStatus == null ? AiSuggestionValidationStatus.VALID : validationStatus;
        inputSnapshotJson = requireText(inputSnapshotJson, "inputSnapshotJson");
        rawModelOutputJson = requireText(rawModelOutputJson, "rawModelOutputJson");
        validatedOutputJson = requireText(validatedOutputJson, "validatedOutputJson");
        Objects.requireNonNull(output, "output must not be null");
        model = normalize(model);
        providerRequestId = normalize(providerRequestId);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
