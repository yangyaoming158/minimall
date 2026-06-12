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
import com.minimall.inventory.repository.InventoryRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class AiInventoryQuestionService {

    static final int DEFAULT_LIMIT = 20;
    static final int DEFAULT_RECORD_LIMIT = 5;

    // SKU-shaped tokens inside the question text; a candidate is trusted as a
    // productId only after it matches an existing inventory row.
    private static final Pattern PRODUCT_ID_TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{2,63}");
    private static final int MAX_PRODUCT_ID_CANDIDATES = 8;

    private final AiInventoryEvidenceFacade evidenceFacade;
    private final AiInventoryAnalysisService analysisService;
    private final InventoryRepository inventoryRepository;

    public AiInventoryQuestionService(
            AiInventoryEvidenceFacade evidenceFacade,
            AiInventoryAnalysisService analysisService,
            InventoryRepository inventoryRepository) {
        this.evidenceFacade = evidenceFacade;
        this.analysisService = analysisService;
        this.inventoryRepository = inventoryRepository;
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
                List.of("问答证据仅使用后端计算的低库存候选。"));
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
                List.of("库存问答为只读查询，不会锁定、扣减或调整库存。"));
    }

    private AiInventoryAskResponse productStatusResponse(AiInventoryAskRequest request, LocalDateTime queryTime) {
        String productId = requireProductId(request, AiInventoryQuestionIntent.PRODUCT_STATUS);
        AiInventoryEvidenceResponse evidence = evidenceFacade.currentInventory(productId, recordLimit(request));
        return analyzedResponse(
                request,
                AiInventoryQuestionIntent.PRODUCT_STATUS,
                queryTime,
                evidence,
                List.of("商品状态由当前库存证据推导。"));
    }

    private AiInventoryAskResponse recentRecordsResponse(AiInventoryAskRequest request, LocalDateTime queryTime) {
        String productId = requireProductId(request, AiInventoryQuestionIntent.RECENT_RECORDS);
        AiInventoryEvidenceResponse evidence = evidenceFacade.currentInventory(productId, recordLimit(request));
        return analyzedResponse(
                request,
                AiInventoryQuestionIntent.RECENT_RECORDS,
                queryTime,
                evidence,
                List.of("流水证据仅限该商品最近的记录。"));
    }

    private AiInventoryAskResponse unsupportedResponse(LocalDateTime queryTime) {
        return new AiInventoryAskResponse(
                AiInventoryQuestionIntent.UNSUPPORTED,
                false,
                "暂不支持该类库存问题。",
                queryTime,
                null,
                List.of("支持的问题类型：当前库存、低库存清单、商品状态、近期流水。"));
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
        if (request.productId() != null && !request.productId().isBlank()) {
            return request.productId().trim();
        }
        String extracted = extractProductIdFromQuestion(request.question());
        if (extracted != null) {
            return extracted;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST,
                "无法识别商品 ID：请填写商品 ID 字段，或在问题中包含完整的商品编号");
    }

    private String extractProductIdFromQuestion(String question) {
        Matcher matcher = PRODUCT_ID_TOKEN.matcher(question);
        int checked = 0;
        while (matcher.find() && checked < MAX_PRODUCT_ID_CANDIDATES) {
            checked++;
            String candidate = matcher.group();
            if (inventoryRepository.findByProductId(candidate).isPresent()) {
                return candidate;
            }
        }
        return null;
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
