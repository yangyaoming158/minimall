package com.minimall.inventory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisRequest;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisResult;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisService;
import com.minimall.inventory.ai.prompt.AiPromptTemplateId;
import com.minimall.inventory.ai.validation.AiAnalysisType;
import com.minimall.inventory.ai.validation.AiOutputProductFacts;
import com.minimall.inventory.domain.AiInventoryQuestionIntent;
import com.minimall.inventory.dto.AiInventoryAskRequest;
import com.minimall.inventory.dto.AiInventoryAskResponse;
import com.minimall.inventory.dto.AiInventoryEvidenceResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AiInventoryQuestionService {

    static final int DEFAULT_LIMIT = 20;
    static final int DEFAULT_RECORD_LIMIT = 5;

    private final AiInventoryEvidenceFacade evidenceFacade;
    private final AiInventoryAnalysisService analysisService;

    public AiInventoryQuestionService(
            AiInventoryEvidenceFacade evidenceFacade,
            AiInventoryAnalysisService analysisService) {
        this.evidenceFacade = evidenceFacade;
        this.analysisService = analysisService;
    }

    public AiInventoryAskResponse answer(AiInventoryAskRequest request) {
        AiInventoryAskRequest normalized = requireRequest(request);
        AiInventoryQuestionIntent intent = detectIntent(normalized.question());
        LocalDateTime queryTime = LocalDateTime.now();
        return switch (intent) {
            case LOW_STOCK_LIST -> lowStockResponse(normalized, queryTime);
            case CURRENT_STOCK -> currentInventoryResponse(normalized, intent, queryTime);
            case PRODUCT_STATUS -> productStatusResponse(normalized, queryTime);
            case RECENT_RECORDS -> recentRecordsResponse(normalized, queryTime);
            case UNSUPPORTED -> unsupportedResponse(queryTime);
        };
    }

    private AiInventoryAskResponse lowStockResponse(AiInventoryAskRequest request, LocalDateTime queryTime) {
        AiInventoryEvidenceResponse evidence =
                evidenceFacade.lowStockCandidates(limit(request), recordLimit(request));
        return analyzedResponse(
                request,
                AiInventoryQuestionIntent.LOW_STOCK_LIST,
                queryTime,
                evidence,
                List.of("Q&A evidence uses backend-computed low-stock candidates only."));
    }

    private AiInventoryAskResponse currentInventoryResponse(
            AiInventoryAskRequest request,
            AiInventoryQuestionIntent intent,
            LocalDateTime queryTime) {
        String productId = requireProductId(request, intent);
        AiInventoryEvidenceResponse evidence = evidenceFacade.currentInventory(productId, recordLimit(request));
        return analyzedResponse(
                request,
                intent,
                queryTime,
                evidence,
                List.of("Inventory Q&A is read-only and does not reserve, deduct, or adjust stock."));
    }

    private AiInventoryAskResponse productStatusResponse(AiInventoryAskRequest request, LocalDateTime queryTime) {
        String productId = requireProductId(request, AiInventoryQuestionIntent.PRODUCT_STATUS);
        AiInventoryEvidenceResponse evidence = evidenceFacade.currentInventory(productId, recordLimit(request));
        return analyzedResponse(
                request,
                AiInventoryQuestionIntent.PRODUCT_STATUS,
                queryTime,
                evidence,
                List.of("Product status is derived from current inventory evidence."));
    }

    private AiInventoryAskResponse recentRecordsResponse(AiInventoryAskRequest request, LocalDateTime queryTime) {
        String productId = requireProductId(request, AiInventoryQuestionIntent.RECENT_RECORDS);
        AiInventoryEvidenceResponse evidence = evidenceFacade.currentInventory(productId, recordLimit(request));
        return analyzedResponse(
                request,
                AiInventoryQuestionIntent.RECENT_RECORDS,
                queryTime,
                evidence,
                List.of("Record evidence is bounded to recent records for the requested product."));
    }

    private AiInventoryAskResponse unsupportedResponse(LocalDateTime queryTime) {
        return new AiInventoryAskResponse(
                AiInventoryQuestionIntent.UNSUPPORTED,
                false,
                "Unsupported inventory question intent.",
                queryTime,
                null,
                List.of("Supported questions cover current stock, low-stock lists, product status, and recent records."));
    }

    private AiInventoryQuestionIntent detectIntent(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "low stock", "low-stock", "below safety", "safety stock", "低库存")) {
            return AiInventoryQuestionIntent.LOW_STOCK_LIST;
        }
        if (containsAny(normalized, "recent record", "records", "movement", "history", "trace", "记录", "流水", "变动")) {
            return AiInventoryQuestionIntent.RECENT_RECORDS;
        }
        if (containsAny(normalized, "status", "state", "health", "healthy", "状态", "健康")) {
            return AiInventoryQuestionIntent.PRODUCT_STATUS;
        }
        if (containsAny(normalized, "current stock", "stock", "inventory", "available", "库存", "现有")) {
            return AiInventoryQuestionIntent.CURRENT_STOCK;
        }
        return AiInventoryQuestionIntent.UNSUPPORTED;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private AiInventoryAskRequest requireRequest(AiInventoryAskRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "question must not be blank");
        }
        return request;
    }

    private String requireProductId(AiInventoryAskRequest request, AiInventoryQuestionIntent intent) {
        if (request.productId() == null || request.productId().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "productId is required for " + intent + " questions");
        }
        return request.productId().trim();
    }

    private int limit(AiInventoryAskRequest request) {
        return request.limit() == null ? DEFAULT_LIMIT : request.limit();
    }

    private int recordLimit(AiInventoryAskRequest request) {
        return request.recordLimit() == null ? DEFAULT_RECORD_LIMIT : request.recordLimit();
    }

    private AiInventoryAskResponse analyzedResponse(
            AiInventoryAskRequest request,
            AiInventoryQuestionIntent intent,
            LocalDateTime queryTime,
            AiInventoryEvidenceResponse evidence,
            List<String> staticLimitations) {
        AiInventoryAnalysisResult analysis = analysisService.generateValidatedAnalysis(
                analysisRequest(request, intent, queryTime, evidence));
        return new AiInventoryAskResponse(
                intent,
                true,
                summary(analysis),
                queryTime,
                evidence,
                limitations(analysis, staticLimitations));
    }

    private AiInventoryAnalysisRequest analysisRequest(
            AiInventoryAskRequest request,
            AiInventoryQuestionIntent intent,
            LocalDateTime queryTime,
            AiInventoryEvidenceResponse evidence) {
        Map<String, Object> inputSnapshot = new LinkedHashMap<>();
        inputSnapshot.put("question", request.question());
        inputSnapshot.put("productId", request.productId());
        inputSnapshot.put("limit", limit(request));
        inputSnapshot.put("recordLimit", recordLimit(request));
        inputSnapshot.put("detectedIntent", intent.name());
        inputSnapshot.put("queryTime", dateValue(queryTime));
        inputSnapshot.put("evidence", evidence);
        return new AiInventoryAnalysisRequest(
                AiPromptTemplateId.INVENTORY_QA,
                AiAnalysisType.INVENTORY_QA,
                inputSnapshot,
                productFacts(evidence),
                allowedDateValues(queryTime, evidence));
    }

    private List<AiOutputProductFacts> productFacts(AiInventoryEvidenceResponse evidence) {
        return evidence.inventories().stream()
                .map(inventory -> new AiOutputProductFacts(
                        inventory.productId(),
                        null,
                        inventory.availableStock(),
                        inventory.lockedStock(),
                        inventory.safetyStock(),
                        null))
                .toList();
    }

    private List<String> allowedDateValues(LocalDateTime queryTime, AiInventoryEvidenceResponse evidence) {
        Set<String> values = new LinkedHashSet<>();
        addDateValue(values, queryTime);
        addDateValue(values, evidence.generatedAt());
        addDateValue(values, evidence.dataFrom());
        addDateValue(values, evidence.dataTo());
        return List.copyOf(values);
    }

    private void addDateValue(Set<String> values, LocalDateTime value) {
        String formatted = dateValue(value);
        if (formatted != null) {
            values.add(formatted);
        }
    }

    private String dateValue(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private String summary(AiInventoryAnalysisResult analysis) {
        return analysis.output().path("summary").asText().trim();
    }

    private List<String> limitations(AiInventoryAnalysisResult analysis, List<String> staticLimitations) {
        Set<String> values = new LinkedHashSet<>(staticLimitations == null ? List.of() : staticLimitations);
        JsonNode limitations = analysis.output().get("limitations");
        if (limitations != null && limitations.isArray()) {
            for (JsonNode limitation : limitations) {
                if (limitation.isTextual() && !limitation.asText().isBlank()) {
                    values.add(limitation.asText().trim());
                }
            }
        }
        return new ArrayList<>(values);
    }
}
