package com.minimall.inventory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.audit.AdminAuditAction;
import com.minimall.common.core.audit.AdminAuditLogWriteRequest;
import com.minimall.common.core.audit.AdminAuditResourceType;
import com.minimall.common.core.audit.AdminAuditSourceType;
import com.minimall.common.core.audit.AdminAuditWriter;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisRequest;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisResult;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisService;
import com.minimall.inventory.ai.prompt.AiPromptTemplateId;
import com.minimall.inventory.ai.validation.AiAnalysisType;
import com.minimall.inventory.ai.validation.AiOutputProductFacts;
import com.minimall.inventory.domain.AiOperationSuggestion;
import com.minimall.inventory.domain.AiOperationSuggestionItem;
import com.minimall.inventory.domain.AiOperationSuggestionSource;
import com.minimall.inventory.domain.AiOperationSuggestionType;
import com.minimall.inventory.domain.AiSuggestionRiskLevel;
import com.minimall.inventory.dto.AiInventorySalesEvidenceResponse;
import com.minimall.inventory.dto.AiInventorySalesItemEvidence;
import com.minimall.inventory.dto.AiSuggestionResponse;
import com.minimall.inventory.repository.AiOperationSuggestionItemRepository;
import com.minimall.inventory.repository.AiOperationSuggestionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiReplenishmentSuggestionService {

    static final int DEFAULT_LIMIT = 20;
    static final int DEFAULT_RECORD_LIMIT = 5;
    static final int HOT_PRODUCT_SALES_DAYS = 7;
    private static final int SUGGESTION_NO_RANDOM_LENGTH = 20;
    private static final int MAX_SUGGESTION_NO_GENERATION_ATTEMPTS = 5;
    private static final int MAX_REASON_LENGTH = 1024;

    private final AiInventoryEvidenceFacade evidenceFacade;
    private final AiInventoryAnalysisService analysisService;
    private final AiOperationSuggestionRepository suggestionRepository;
    private final AiOperationSuggestionItemRepository itemRepository;
    private final AdminAuditWriter adminAuditWriter;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public AiReplenishmentSuggestionService(
            AiInventoryEvidenceFacade evidenceFacade,
            AiInventoryAnalysisService analysisService,
            AiOperationSuggestionRepository suggestionRepository,
            AiOperationSuggestionItemRepository itemRepository,
            AdminAuditWriter adminAuditWriter,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.evidenceFacade = evidenceFacade;
        this.analysisService = analysisService;
        this.suggestionRepository = suggestionRepository;
        this.itemRepository = itemRepository;
        this.adminAuditWriter = adminAuditWriter;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Generates one validated replenishment suggestion: evidence reads and the
     * provider call run outside the persistence transaction so a slow model
     * cannot hold a database transaction open; only the validated result is
     * persisted, atomically, as PENDING_REVIEW. Inventory is never changed and
     * no inbound draft is created.
     */
    public AiSuggestionResponse generate(
            Integer requestedLimit,
            Integer requestedRecordLimit,
            InventoryAdminAuditContext auditContext) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
        int recordLimit = requestedRecordLimit == null ? DEFAULT_RECORD_LIMIT : requestedRecordLimit;
        LocalDateTime queryTime = LocalDateTime.now();

        AiInventorySalesEvidenceResponse lowStockEvidence =
                evidenceFacade.lowStockAnalysisEvidence(limit, recordLimit);
        AiInventorySalesEvidenceResponse hotProductsEvidence =
                evidenceFacade.hotProductsEvidence(HOT_PRODUCT_SALES_DAYS, limit, recordLimit);
        List<AiOutputProductFacts> productFacts = mergedProductFacts(lowStockEvidence, hotProductsEvidence);
        if (productFacts.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "No replenishment evidence is available for suggestion generation");
        }

        AiInventoryAnalysisResult analysis = analysisService.generateValidatedAnalysis(analysisRequest(
                limit, recordLimit, queryTime, lowStockEvidence, hotProductsEvidence, productFacts));
        List<ValidatedItem> validatedItems = validatedItems(analysis);
        if (validatedItems.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Replenishment analysis produced no actionable items");
        }

        return transactionTemplate.execute(status ->
                persistSuggestion(analysis, validatedItems, lowStockEvidence, hotProductsEvidence, auditContext));
    }

    private AiSuggestionResponse persistSuggestion(
            AiInventoryAnalysisResult analysis,
            List<ValidatedItem> validatedItems,
            AiInventorySalesEvidenceResponse lowStockEvidence,
            AiInventorySalesEvidenceResponse hotProductsEvidence,
            InventoryAdminAuditContext auditContext) {
        String suggestionNo = generateSuggestionNo();
        AiOperationSuggestion suggestion = new AiOperationSuggestion(
                suggestionNo,
                AiOperationSuggestionType.REPLENISHMENT,
                AiOperationSuggestionSource.AI_MODEL,
                truncate(summary(analysis), MAX_REASON_LENGTH),
                null,
                inputSummary(lowStockEvidence, hotProductsEvidence, validatedItems.size()));
        suggestion.recordAiMetadata(
                analysis.provider().name(),
                analysis.model(),
                analysis.promptVersion(),
                analysis.outputSchemaVersion(),
                analysis.validationStatus(),
                null,
                analysis.inputSnapshotJson(),
                analysis.validatedOutputJson(),
                analysis.rawModelOutputJson());
        AiOperationSuggestion saved = suggestionRepository.saveAndFlush(suggestion);

        List<AiOperationSuggestionItem> items = itemRepository.saveAllAndFlush(validatedItems.stream()
                .map(item -> item.toEntity(saved.getSuggestionNo()))
                .toList());

        AiSuggestionResponse response = AiSuggestionResponse.from(saved, items);
        writeCreateAudit(auditContext, saved.getSuggestionNo(), response, items.size());
        return response;
    }

    private AiInventoryAnalysisRequest analysisRequest(
            int limit,
            int recordLimit,
            LocalDateTime queryTime,
            AiInventorySalesEvidenceResponse lowStockEvidence,
            AiInventorySalesEvidenceResponse hotProductsEvidence,
            List<AiOutputProductFacts> productFacts) {
        Map<String, Object> inputSnapshot = new LinkedHashMap<>();
        inputSnapshot.put("analysisType", AiAnalysisType.REPLENISHMENT.name());
        inputSnapshot.put("limit", limit);
        inputSnapshot.put("recordLimit", recordLimit);
        inputSnapshot.put("queryTime", dateValue(queryTime));
        inputSnapshot.put("lowStockEvidence", lowStockEvidence);
        inputSnapshot.put("hotProductsEvidence", hotProductsEvidence);
        return new AiInventoryAnalysisRequest(
                AiPromptTemplateId.REPLENISHMENT_SUGGESTION,
                AiAnalysisType.REPLENISHMENT,
                inputSnapshot,
                productFacts,
                allowedDateValues(queryTime, lowStockEvidence, hotProductsEvidence));
    }

    private List<AiOutputProductFacts> mergedProductFacts(
            AiInventorySalesEvidenceResponse lowStockEvidence,
            AiInventorySalesEvidenceResponse hotProductsEvidence) {
        Map<String, AiOutputProductFacts> factsByProductId = new LinkedHashMap<>();
        addProductFacts(factsByProductId, lowStockEvidence);
        addProductFacts(factsByProductId, hotProductsEvidence);
        return List.copyOf(factsByProductId.values());
    }

    private void addProductFacts(
            Map<String, AiOutputProductFacts> factsByProductId,
            AiInventorySalesEvidenceResponse evidence) {
        for (AiInventorySalesItemEvidence product : evidence.products()) {
            if (product.inventory() == null || factsByProductId.containsKey(product.productId())) {
                continue;
            }
            factsByProductId.put(product.productId(), new AiOutputProductFacts(
                    product.productId(),
                    null,
                    product.inventory().availableStock(),
                    product.inventory().lockedStock(),
                    product.inventory().safetyStock(),
                    product.sales() == null ? null : product.sales().soldQuantity()));
        }
    }

    private List<String> allowedDateValues(
            LocalDateTime queryTime,
            AiInventorySalesEvidenceResponse lowStockEvidence,
            AiInventorySalesEvidenceResponse hotProductsEvidence) {
        Set<String> values = new LinkedHashSet<>();
        addDateValue(values, queryTime);
        for (AiInventorySalesEvidenceResponse evidence : List.of(lowStockEvidence, hotProductsEvidence)) {
            addDateValue(values, evidence.generatedAt());
            addDateValue(values, evidence.dataFrom());
            addDateValue(values, evidence.dataTo());
        }
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

    private List<ValidatedItem> validatedItems(AiInventoryAnalysisResult analysis) {
        JsonNode items = analysis.output().get("items");
        if (items == null || !items.isArray()) {
            return List.of();
        }
        List<ValidatedItem> validated = new ArrayList<>();
        for (JsonNode item : items) {
            if (item.isObject()) {
                validated.add(new ValidatedItem(
                        text(item, "productId"),
                        text(item, "productName"),
                        integer(item, "availableStock"),
                        integer(item, "lockedStock"),
                        integer(item, "safetyStock"),
                        integer(item, "soldQuantityLast7Days"),
                        item.path("suggestedQuantity").asInt(),
                        riskLevel(item),
                        text(item, "reason")));
            }
        }
        return validated;
    }

    private AiSuggestionRiskLevel riskLevel(JsonNode item) {
        String value = text(item, "riskLevel");
        return value == null ? null : AiSuggestionRiskLevel.valueOf(value);
    }

    private Integer integer(JsonNode item, String field) {
        JsonNode value = item.get(field);
        return value == null || value.isNull() ? null : value.asInt();
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

    private String inputSummary(
            AiInventorySalesEvidenceResponse lowStockEvidence,
            AiInventorySalesEvidenceResponse hotProductsEvidence,
            int itemCount) {
        return "Replenishment evidence: " + lowStockEvidence.products().size()
                + " low-stock candidate(s), " + hotProductsEvidence.products().size()
                + " hot product(s) over " + HOT_PRODUCT_SALES_DAYS + " day(s), "
                + itemCount + " suggested item(s).";
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String generateSuggestionNo() {
        for (int attempt = 0; attempt < MAX_SUGGESTION_NO_GENERATION_ATTEMPTS; attempt++) {
            String suggestionNo = "AIS-" + UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, SUGGESTION_NO_RANDOM_LENGTH)
                    .toUpperCase(Locale.ROOT);
            if (!suggestionRepository.existsBySuggestionNo(suggestionNo)) {
                return suggestionNo;
            }
        }
        throw new BusinessException(ErrorCode.CONFLICT, "Unable to allocate AI suggestion number");
    }

    private void writeCreateAudit(
            InventoryAdminAuditContext auditContext,
            String suggestionNo,
            AiSuggestionResponse after,
            int itemCount) {
        adminAuditWriter.write(new AdminAuditLogWriteRequest(
                auditContext.adminUserId(),
                auditContext.adminUsername(),
                AdminAuditAction.AI_SUGGESTION_CREATE,
                AdminAuditResourceType.AI_SUGGESTION,
                suggestionNo,
                auditContext.requestId(),
                AdminAuditSourceType.AI_SUGGESTION,
                suggestionNo,
                null,
                objectMapper.valueToTree(after),
                auditContext.ip(),
                auditContext.userAgent(),
                "Generate AI replenishment suggestion " + suggestionNo + " with " + itemCount + " item(s)"));
    }

    private record ValidatedItem(
            String productId,
            String productName,
            Integer availableStock,
            Integer lockedStock,
            Integer safetyStock,
            Integer soldQuantityLast7Days,
            int suggestedQuantity,
            AiSuggestionRiskLevel riskLevel,
            String reason) {

        AiOperationSuggestionItem toEntity(String suggestionNo) {
            return new AiOperationSuggestionItem(
                    suggestionNo,
                    productId,
                    productName,
                    availableStock,
                    lockedStock,
                    safetyStock,
                    soldQuantityLast7Days,
                    suggestedQuantity,
                    riskLevel,
                    reason);
        }
    }
}
