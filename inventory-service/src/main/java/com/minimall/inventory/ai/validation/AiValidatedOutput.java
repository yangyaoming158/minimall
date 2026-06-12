package com.minimall.inventory.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

public record AiValidatedOutput(
        AiAnalysisType analysisType,
        JsonNode output,
        String canonicalJson) {

    public AiValidatedOutput {
        Objects.requireNonNull(analysisType, "analysisType must not be null");
        Objects.requireNonNull(output, "output must not be null");
        if (canonicalJson == null || canonicalJson.isBlank()) {
            throw new IllegalArgumentException("canonicalJson must not be blank");
        }
    }
}
