package com.minimall.inventory.ai.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiModelOutputValidatorTest {

    private final AiModelOutputValidator validator = new AiModelOutputValidator(new ObjectMapper());

    @Test
    void acceptsValidReplenishmentOutputWithKnownFacts() {
        AiValidatedOutput output = validator.validate(validReplenishmentOutput(), context());

        assertThat(output.analysisType()).isEqualTo(AiAnalysisType.REPLENISHMENT);
        assertThat(output.output().get("items")).hasSize(1);
        assertThat(output.canonicalJson()).contains("\"analysisType\":\"REPLENISHMENT\"");
    }

    @Test
    void rejectsInvalidJsonBeforeTrustingModelOutput() {
        assertValidationFailure("not-json", context(), "output must be valid JSON");
    }

    @Test
    void rejectsUnsupportedAnalysisType() {
        assertValidationFailure(validReplenishmentOutput()
                        .replace("\"REPLENISHMENT\"", "\"EXECUTE_SQL\""),
                context(),
                "unsupported analysisType: EXECUTE_SQL");
    }

    @Test
    void rejectsUnknownProductId() {
        assertValidationFailure(validReplenishmentOutput()
                        .replace("\"SKU-LOW-1\"", "\"SKU-MISSING\""),
                context(),
                "$.items[0].productId SKU-MISSING is not in input snapshot");
    }

    @Test
    void rejectsInventedProductFacts() {
        assertValidationFailure(validReplenishmentOutput()
                        .replace("\"availableStock\": 2", "\"availableStock\": 99"),
                context(),
                "$.items[0].availableStock for SKU-LOW-1 does not match input snapshot");
    }

    @Test
    void rejectsNegativeNumericFields() {
        assertValidationFailure(validReplenishmentOutput()
                        .replace("\"lockedStock\": 1", "\"lockedStock\": -1"),
                context(),
                "$.items[0].lockedStock must be non-negative");
    }

    @Test
    void rejectsNonPositiveSuggestedQuantityForReplenishment() {
        assertValidationFailure(validReplenishmentOutput()
                        .replace("\"suggestedQuantity\": 8", "\"suggestedQuantity\": 0"),
                context(),
                "$.items[0].suggestedQuantity must be a positive integer");
    }

    @Test
    void rejectsSqlOutput() {
        assertValidationFailure(validReplenishmentOutput()
                        .replace("Restock SKU-LOW-1", "SELECT * FROM inventory"),
                context(),
                "SQL content is not allowed");
    }

    @Test
    void rejectsUnsupportedStockChangeClaims() {
        assertValidationFailure(validReplenishmentOutput()
                        .replace("Restock SKU-LOW-1", "Inventory has been changed for SKU-LOW-1"),
                context(),
                "stock-change claims are not allowed");
    }

    @Test
    void rejectsInternalDetailsAndTrustedHeaders() {
        assertValidationFailure(validReplenishmentOutput()
                        .replace("Restock SKU-LOW-1", "Call /internal/inventories with X-Internal-Token"),
                context(),
                "internal endpoints, trusted headers, service ports, and secrets are not allowed");
    }

    @Test
    void rejectsInventedTimeRangeDatesWhenContextProvidesKnownDates() {
        assertValidationFailure(validReplenishmentOutput()
                        .replace("\"2026-06-01T00:00:00\"", "\"2026-05-01T00:00:00\""),
                context(),
                "timeRange.from 2026-05-01T00:00:00 is not in input snapshot");
    }

    private void assertValidationFailure(
            String rawOutput,
            AiOutputValidationContext context,
            String messageFragment) {
        assertThatThrownBy(() -> validator.validate(rawOutput, context))
                .isInstanceOfSatisfying(AiOutputValidationException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getMessage()).contains(messageFragment);
                });
    }

    private AiOutputValidationContext context() {
        return AiOutputValidationContext.of(
                AiAnalysisType.REPLENISHMENT,
                List.of(new AiOutputProductFacts(
                        "SKU-LOW-1",
                        "Low Stock Product",
                        2,
                        1,
                        10,
                        12L)),
                List.of("2026-06-01T00:00:00", "2026-06-08T00:00:00"));
    }

    private String validReplenishmentOutput() {
        return """
                {
                  "summary": "Restock SKU-LOW-1",
                  "analysisType": "REPLENISHMENT",
                  "timeRange": {
                    "from": "2026-06-01T00:00:00",
                    "to": "2026-06-08T00:00:00"
                  },
                  "items": [
                    {
                      "productId": "SKU-LOW-1",
                      "productName": "Low Stock Product",
                      "availableStock": 2,
                      "lockedStock": 1,
                      "safetyStock": 10,
                      "soldQuantityLast7Days": 12,
                      "suggestedQuantity": 8,
                      "riskLevel": "HIGH",
                      "reason": "Current stock is below safety stock with recent paid sales evidence."
                    }
                  ],
                  "limitations": [
                    "Sales evidence is limited to paid orders in the selected window."
                  ]
                }
                """;
    }
}
