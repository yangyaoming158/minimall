package com.minimall.inventory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisRequest;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisResult;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisService;
import com.minimall.inventory.ai.prompt.AiPromptTemplateId;
import com.minimall.inventory.ai.validation.AiAnalysisType;
import com.minimall.inventory.ai.validation.AiOutputProductFacts;
import com.minimall.inventory.domain.AiSuggestionRiskLevel;
import com.minimall.inventory.dto.AiInventoryAnalysisItemResponse;
import com.minimall.inventory.dto.AiInventoryAnalysisResponse;
import com.minimall.inventory.dto.AiInventoryHotProductsAnalysisRequest;
import com.minimall.inventory.dto.AiInventoryLowStockAnalysisRequest;
import com.minimall.inventory.dto.AiInventorySalesEvidenceResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AiInventoryAnalysisApiService {

    static final int DEFAULT_LIMIT = 20;
    static final int DEFAULT_RECORD_LIMIT = 5;
    static final int DEFAULT_HOT_PRODUCT_DAYS = 7;

    private final AiInventoryEvidenceFacade evidenceFacade;
    private final AiInventoryAnalysisService analysisService;

    public AiInventoryAnalysisApiService(
            AiInventoryEvidenceFacade evidenceFacade,
            AiInventoryAnalysisService analysisService) {
        this.evidenceFacade = evidenceFacade;
        this.analysisService = analysisService;
    }

    public AiInventoryAnalysisResponse lowStockAnalysis(AiInventoryLowStockAnalysisRequest request) {
        AiInventoryLowStockAnalysisRequest normalized = request == null
                ? new AiInventoryLowStockAnalysisRequest(null, null)
                : request;
        LocalDateTime queryTime = LocalDateTime.now();
        AiInventorySalesEvidenceResponse evidence =
                evidenceFacade.lowStockAnalysisEvidence(limit(normalized), recordLimit(normalized));
        AiInventoryAnalysisResult analysis = analysisService.generateValidatedAnalysis(
                lowStockAnalysisRequest(normalized, queryTime, evidence));
        return new AiInventoryAnalysisResponse(
                analysis.analysisType(),
                summary(analysis),
                queryTime,
                evidence,
                items(analysis),
                limitations(evidence, analysis));
    }

    public AiInventoryAnalysisResponse hotProductsAnalysis(AiInventoryHotProductsAnalysisRequest request) {
        AiInventoryHotProductsAnalysisRequest normalized = request == null
                ? new AiInventoryHotProductsAnalysisRequest(null, null, null)
                : request;
        LocalDateTime queryTime = LocalDateTime.now();
        AiInventorySalesEvidenceResponse evidence = evidenceFacade.hotProductsEvidence(
                days(normalized),
                limit(normalized),
                recordLimit(normalized));
        AiInventoryAnalysisResult analysis = analysisService.generateValidatedAnalysis(
                hotProductsAnalysisRequest(normalized, queryTime, evidence));
        return new AiInventoryAnalysisResponse(
                analysis.analysisType(),
                summary(analysis),
                queryTime,
                evidence,
                items(analysis),
                limitations(evidence, analysis));
    }

    private AiInventoryAnalysisRequest lowStockAnalysisRequest(
            AiInventoryLowStockAnalysisRequest request,
            LocalDateTime queryTime,
            AiInventorySalesEvidenceResponse evidence) {
        Map<String, Object> inputSnapshot = new LinkedHashMap<>();
        inputSnapshot.put("analysisType", AiAnalysisType.LOW_STOCK.name());
        inputSnapshot.put("limit", limit(request));
        inputSnapshot.put("recordLimit", recordLimit(request));
        inputSnapshot.put("queryTime", dateValue(queryTime));
        inputSnapshot.put("evidence", evidence);
        return new AiInventoryAnalysisRequest(
                AiPromptTemplateId.LOW_STOCK_ANALYSIS,
                AiAnalysisType.LOW_STOCK,
                inputSnapshot,
                productFacts(evidence),
                allowedDateValues(queryTime, evidence));
    }

    private AiInventoryAnalysisRequest hotProductsAnalysisRequest(
            AiInventoryHotProductsAnalysisRequest request,
            LocalDateTime queryTime,
            AiInventorySalesEvidenceResponse evidence) {
        Map<String, Object> inputSnapshot = new LinkedHashMap<>();
        inputSnapshot.put("analysisType", AiAnalysisType.HOT_PRODUCTS.name());
        inputSnapshot.put("days", days(request));
        inputSnapshot.put("limit", limit(request));
        inputSnapshot.put("recordLimit", recordLimit(request));
        inputSnapshot.put("queryTime", dateValue(queryTime));
        inputSnapshot.put("evidence", evidence);
        return new AiInventoryAnalysisRequest(
                AiPromptTemplateId.HOT_PRODUCTS_ANALYSIS,
                AiAnalysisType.HOT_PRODUCTS,
                inputSnapshot,
                productFacts(evidence),
                allowedDateValues(queryTime, evidence));
    }

    private List<AiOutputProductFacts> productFacts(AiInventorySalesEvidenceResponse evidence) {
        return evidence.products().stream()
                .map(product -> new AiOutputProductFacts(
                        product.productId(),
                        null,
                        product.inventory() == null ? null : product.inventory().availableStock(),
                        product.inventory() == null ? null : product.inventory().lockedStock(),
                        product.inventory() == null ? null : product.inventory().safetyStock(),
                        product.sales() == null ? null : product.sales().soldQuantity()))
                .toList();
    }

    private List<String> allowedDateValues(
            LocalDateTime queryTime,
            AiInventorySalesEvidenceResponse evidence) {
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

    private List<AiInventoryAnalysisItemResponse> items(AiInventoryAnalysisResult analysis) {
        JsonNode items = analysis.output().get("items");
        if (items == null || !items.isArray()) {
            return List.of();
        }
        List<AiInventoryAnalysisItemResponse> responses = new ArrayList<>();
        for (JsonNode item : items) {
            if (item.isObject()) {
                responses.add(new AiInventoryAnalysisItemResponse(
                        text(item, "productId"),
                        text(item, "productName"),
                        integer(item, "availableStock"),
                        integer(item, "lockedStock"),
                        integer(item, "safetyStock"),
                        longValue(item, "soldQuantityLast7Days"),
                        riskLevel(item),
                        text(item, "reason")));
            }
        }
        return responses;
    }

    private AiSuggestionRiskLevel riskLevel(JsonNode item) {
        String value = text(item, "riskLevel");
        return value == null ? null : AiSuggestionRiskLevel.valueOf(value);
    }

    private Integer integer(JsonNode item, String field) {
        JsonNode value = item.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private Long longValue(JsonNode item, String field) {
        JsonNode value = item.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private String text(JsonNode item, String field) {
        JsonNode value = item.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String summary(AiInventoryAnalysisResult analysis) {
        return analysis.output().path("summary").asText().trim();
    }

    private List<String> limitations(
            AiInventorySalesEvidenceResponse evidence,
            AiInventoryAnalysisResult analysis) {
        Set<String> values = new LinkedHashSet<>(evidence.limitations());
        JsonNode limitations = analysis.output().get("limitations");
        if (limitations != null && limitations.isArray()) {
            for (JsonNode limitation : limitations) {
                if (limitation.isTextual() && !limitation.asText().isBlank()) {
                    values.add(limitation.asText().trim());
                }
            }
        }
        return List.copyOf(values);
    }

    private int limit(AiInventoryLowStockAnalysisRequest request) {
        return request.limit() == null ? DEFAULT_LIMIT : request.limit();
    }

    private int recordLimit(AiInventoryLowStockAnalysisRequest request) {
        return request.recordLimit() == null ? DEFAULT_RECORD_LIMIT : request.recordLimit();
    }

    private int days(AiInventoryHotProductsAnalysisRequest request) {
        return request.days() == null ? DEFAULT_HOT_PRODUCT_DAYS : request.days();
    }

    private int limit(AiInventoryHotProductsAnalysisRequest request) {
        return request.limit() == null ? DEFAULT_LIMIT : request.limit();
    }

    private int recordLimit(AiInventoryHotProductsAnalysisRequest request) {
        return request.recordLimit() == null ? DEFAULT_RECORD_LIMIT : request.recordLimit();
    }
}
