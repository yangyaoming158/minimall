package com.minimall.inventory.ai.analysis;

import com.minimall.inventory.ai.prompt.AiPromptTemplateId;
import com.minimall.inventory.ai.validation.AiAnalysisType;
import com.minimall.inventory.ai.validation.AiOutputProductFacts;
import com.minimall.inventory.ai.validation.AiOutputValidationContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AiInventoryAnalysisRequest(
        AiPromptTemplateId templateId,
        AiAnalysisType expectedAnalysisType,
        Object inputSnapshot,
        List<AiOutputProductFacts> productFacts,
        List<String> allowedDateValues) {

    public AiInventoryAnalysisRequest {
        Objects.requireNonNull(templateId, "templateId must not be null");
        Objects.requireNonNull(expectedAnalysisType, "expectedAnalysisType must not be null");
        inputSnapshot = inputSnapshot == null ? Map.of() : inputSnapshot;
        productFacts = productFacts == null ? List.of() : List.copyOf(productFacts);
        allowedDateValues = allowedDateValues == null ? List.of() : List.copyOf(allowedDateValues);
    }

    public AiOutputValidationContext validationContext() {
        return AiOutputValidationContext.of(expectedAnalysisType, productFacts, allowedDateValues);
    }
}
