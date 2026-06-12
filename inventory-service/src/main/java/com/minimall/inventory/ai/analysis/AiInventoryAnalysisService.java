package com.minimall.inventory.ai.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.inventory.ai.AiProvider;
import com.minimall.inventory.ai.AiProviderMessage;
import com.minimall.inventory.ai.AiProviderRequest;
import com.minimall.inventory.ai.AiProviderResponse;
import com.minimall.inventory.ai.prompt.AiPromptTemplate;
import com.minimall.inventory.ai.prompt.AiPromptTemplateCatalog;
import com.minimall.inventory.ai.validation.AiModelOutputValidator;
import com.minimall.inventory.ai.validation.AiValidatedOutput;
import com.minimall.inventory.domain.AiSuggestionValidationStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class AiInventoryAnalysisService {

    private final AiProvider aiProvider;
    private final AiPromptTemplateCatalog promptTemplateCatalog;
    private final AiModelOutputValidator outputValidator;
    private final ObjectMapper objectMapper;

    public AiInventoryAnalysisService(
            AiProvider aiProvider,
            AiPromptTemplateCatalog promptTemplateCatalog,
            AiModelOutputValidator outputValidator,
            ObjectMapper objectMapper) {
        this.aiProvider = Objects.requireNonNull(aiProvider, "aiProvider must not be null");
        this.promptTemplateCatalog =
                Objects.requireNonNull(promptTemplateCatalog, "promptTemplateCatalog must not be null");
        this.outputValidator = Objects.requireNonNull(outputValidator, "outputValidator must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public AiInventoryAnalysisResult generateValidatedAnalysis(AiInventoryAnalysisRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        AiPromptTemplate template = promptTemplateCatalog.get(request.templateId());
        String inputSnapshotJson = toJson(request.inputSnapshot(), "AI input snapshot");
        AiProviderResponse providerResponse = aiProvider.generate(providerRequest(template, request, inputSnapshotJson));
        AiValidatedOutput validatedOutput =
                outputValidator.validate(providerResponse.content(), request.validationContext());
        return new AiInventoryAnalysisResult(
                request.templateId(),
                validatedOutput.analysisType(),
                template.promptVersion(),
                template.outputSchemaVersion(),
                providerResponse.provider(),
                providerResponse.model(),
                providerResponse.requestId(),
                providerResponse.tokenUsage(),
                AiSuggestionValidationStatus.VALID,
                inputSnapshotJson,
                providerResponse.content(),
                validatedOutput.canonicalJson(),
                validatedOutput.output());
    }

    private AiProviderRequest providerRequest(
            AiPromptTemplate template,
            AiInventoryAnalysisRequest request,
            String inputSnapshotJson) {
        return new AiProviderRequest(
                template.promptVersion(),
                template.outputSchemaVersion(),
                List.of(
                        AiProviderMessage.system(template.systemPrompt()),
                        AiProviderMessage.user(template.taskPrompt() + "\n\nInput snapshot JSON:\n"
                                + inputSnapshotJson)),
                providerInput(request));
    }

    private Map<String, Object> providerInput(AiInventoryAnalysisRequest request) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("templateId", request.templateId().name());
        input.put("analysisType", request.expectedAnalysisType().name());
        input.put("inputSnapshot", request.inputSnapshot());
        input.put("productFacts", request.productFacts());
        input.put("allowedDateValues", request.allowedDateValues());
        return input;
    }

    private String toJson(Object value, String subject) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to serialize " + subject, exception);
        }
    }
}
