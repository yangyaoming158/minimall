package com.minimall.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "ai_operation_suggestion_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_operation_suggestion_item_product",
                columnNames = {"suggestion_no", "product_id"}),
        indexes = {
                @Index(name = "idx_ai_operation_suggestion_item_suggestion_no", columnList = "suggestion_no"),
                @Index(name = "idx_ai_operation_suggestion_item_product_id", columnList = "product_id")
        })
public class AiOperationSuggestionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "suggestion_no", nullable = false, length = 64)
    private String suggestionNo;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(name = "product_name", length = 128)
    private String productName;

    @Column(name = "available_stock")
    private Integer availableStock;

    @Column(name = "locked_stock")
    private Integer lockedStock;

    @Column(name = "safety_stock")
    private Integer safetyStock;

    @Column(name = "sold_quantity_last_7_days")
    private Integer soldQuantityLast7Days;

    @Column(name = "suggested_quantity", nullable = false)
    private int suggestedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 32)
    private AiSuggestionRiskLevel riskLevel = AiSuggestionRiskLevel.MEDIUM;

    @Column(length = 1024)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AiOperationSuggestionItem() {
    }

    public AiOperationSuggestionItem(
            String suggestionNo,
            String productId,
            String productName,
            Integer availableStock,
            Integer lockedStock,
            Integer safetyStock,
            Integer soldQuantityLast7Days,
            int suggestedQuantity,
            AiSuggestionRiskLevel riskLevel,
            String reason) {
        if (suggestedQuantity <= 0) {
            throw new IllegalArgumentException("suggestedQuantity must be positive");
        }
        validateNonNegative(availableStock, "availableStock");
        validateNonNegative(lockedStock, "lockedStock");
        validateNonNegative(safetyStock, "safetyStock");
        validateNonNegative(soldQuantityLast7Days, "soldQuantityLast7Days");
        this.suggestionNo = normalize(suggestionNo);
        this.productId = normalize(productId);
        this.productName = normalize(productName);
        this.availableStock = availableStock;
        this.lockedStock = lockedStock;
        this.safetyStock = safetyStock;
        this.soldQuantityLast7Days = soldQuantityLast7Days;
        this.suggestedQuantity = suggestedQuantity;
        this.riskLevel = riskLevel == null ? AiSuggestionRiskLevel.MEDIUM : riskLevel;
        this.reason = normalize(reason);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (riskLevel == null) {
            riskLevel = AiSuggestionRiskLevel.MEDIUM;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getSuggestionNo() {
        return suggestionNo;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public Integer getAvailableStock() {
        return availableStock;
    }

    public Integer getLockedStock() {
        return lockedStock;
    }

    public Integer getSafetyStock() {
        return safetyStock;
    }

    public Integer getSoldQuantityLast7Days() {
        return soldQuantityLast7Days;
    }

    public int getSuggestedQuantity() {
        return suggestedQuantity;
    }

    public AiSuggestionRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    private static void validateNonNegative(Integer value, String fieldName) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
