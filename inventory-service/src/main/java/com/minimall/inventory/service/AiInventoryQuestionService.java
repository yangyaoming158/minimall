package com.minimall.inventory.service;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.inventory.domain.AiInventoryQuestionIntent;
import com.minimall.inventory.dto.AiInventoryAskRequest;
import com.minimall.inventory.dto.AiInventoryAskResponse;
import com.minimall.inventory.dto.AiInventoryEvidenceResponse;
import com.minimall.inventory.dto.AiInventoryItemEvidence;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class AiInventoryQuestionService {

    static final int DEFAULT_LIMIT = 20;
    static final int DEFAULT_RECORD_LIMIT = 5;

    private final AiInventoryEvidenceFacade evidenceFacade;

    public AiInventoryQuestionService(AiInventoryEvidenceFacade evidenceFacade) {
        this.evidenceFacade = evidenceFacade;
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
        return new AiInventoryAskResponse(
                AiInventoryQuestionIntent.LOW_STOCK_LIST,
                true,
                "Found " + evidence.inventories().size() + " low-stock inventory candidate(s).",
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
        AiInventoryItemEvidence inventory = firstInventory(evidence, productId);
        return new AiInventoryAskResponse(
                intent,
                true,
                "Current stock for " + productId + " is " + inventory.availableStock()
                        + " available and " + inventory.lockedStock() + " locked.",
                queryTime,
                evidence,
                List.of("Inventory Q&A is read-only and does not reserve, deduct, or adjust stock."));
    }

    private AiInventoryAskResponse productStatusResponse(AiInventoryAskRequest request, LocalDateTime queryTime) {
        String productId = requireProductId(request, AiInventoryQuestionIntent.PRODUCT_STATUS);
        AiInventoryEvidenceResponse evidence = evidenceFacade.currentInventory(productId, recordLimit(request));
        AiInventoryItemEvidence inventory = firstInventory(evidence, productId);
        return new AiInventoryAskResponse(
                AiInventoryQuestionIntent.PRODUCT_STATUS,
                true,
                "Product " + productId + " is " + inventory.status()
                        + " with stock state " + inventory.stockState()
                        + (inventory.lowStock() ? " and is below safety stock." : " and is not below safety stock."),
                queryTime,
                evidence,
                List.of("Product status is derived from current inventory evidence."));
    }

    private AiInventoryAskResponse recentRecordsResponse(AiInventoryAskRequest request, LocalDateTime queryTime) {
        String productId = requireProductId(request, AiInventoryQuestionIntent.RECENT_RECORDS);
        AiInventoryEvidenceResponse evidence = evidenceFacade.currentInventory(productId, recordLimit(request));
        return new AiInventoryAskResponse(
                AiInventoryQuestionIntent.RECENT_RECORDS,
                true,
                "Found " + evidence.records().size() + " recent inventory record(s) for " + productId + ".",
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

    private AiInventoryItemEvidence firstInventory(AiInventoryEvidenceResponse evidence, String productId) {
        return evidence.inventories().stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "Inventory evidence not found for " + productId));
    }
}
