package com.minimall.inventory.ai.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.inventory.domain.AiSuggestionRiskLevel;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AiModelOutputValidator {

    private static final Pattern SQL_PATTERN = Pattern.compile(
            "\\b(select\\s+.+\\s+from|insert\\s+into|update\\s+\\w+\\s+set|delete\\s+from|drop\\s+table|"
                    + "alter\\s+table|truncate\\s+table|create\\s+table)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INTERNAL_DETAIL_PATTERN = Pattern.compile(
            "(/internal\\b|x-internal-token|x-user-id|x-username|x-user-role|api[_ -]?key|secret|"
                    + "localhost:\\d{2,5}|127\\.0\\.0\\.1:\\d{2,5})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STOCK_CHANGE_CLAIM_PATTERN = Pattern.compile(
            "\\b(stock|inventory)\\s+(has\\s+been\\s+|have\\s+been\\s+|has\\s+|have\\s+|already\\s+|"
                    + "was\\s+|were\\s+|is\\s+now\\s+)?"
                    + "(changed|updated|increased|decreased|adjusted|modified|deducted|released|applied)\\b"
                    + "|\\b(applied|confirmed|executed)\\s+(the\\s+)?(inventory|stock|inbound order|replenishment)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> FORBIDDEN_STOCK_CHANGE_FIELDS = Set.of(
            "inventoryChanged",
            "stockChanged",
            "stockChangeApplied",
            "inventoryRecordId",
            "inboundNo",
            "confirmRequestId",
            "confirmedAt",
            "appliedAt");
    private static final Set<String> FACT_NUMERIC_FIELDS = Set.of(
            "availableStock",
            "lockedStock",
            "safetyStock",
            "soldQuantityLast7Days");

    private final ObjectMapper objectMapper;

    public AiModelOutputValidator(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public AiValidatedOutput validate(String rawOutput, AiOutputValidationContext context) {
        Objects.requireNonNull(context, "context must not be null");
        JsonNode root = parse(rawOutput);
        if (!root.isObject()) {
            fail("output must be a JSON object");
        }

        requireTextField(root, "summary");
        AiAnalysisType analysisType = analysisType(root, context.expectedAnalysisType());
        validateNumericValues(root, "$");
        validateForbiddenContent(root, "$");
        validateTimeRange(root.get("timeRange"), context);
        validateLimitations(root.get("limitations"));
        validateItems(root.get("items"), context, analysisType);

        return new AiValidatedOutput(analysisType, root, canonicalJson(root));
    }

    private JsonNode parse(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            fail("output must not be blank");
        }
        try {
            return objectMapper.readTree(rawOutput);
        } catch (JsonProcessingException exception) {
            throw new AiOutputValidationException("output must be valid JSON");
        }
    }

    private AiAnalysisType analysisType(JsonNode root, AiAnalysisType expectedAnalysisType) {
        String rawAnalysisType = requireTextField(root, "analysisType");
        try {
            AiAnalysisType analysisType = AiAnalysisType.from(rawAnalysisType);
            if (analysisType != expectedAnalysisType) {
                fail("analysisType " + analysisType + " does not match expected " + expectedAnalysisType);
            }
            return analysisType;
        } catch (IllegalArgumentException exception) {
            throw new AiOutputValidationException(exception.getMessage());
        }
    }

    private void validateItems(
            JsonNode items,
            AiOutputValidationContext context,
            AiAnalysisType analysisType) {
        if (items == null || items.isNull()) {
            fail("items must be an array");
        }
        if (!items.isArray()) {
            fail("items must be an array");
        }
        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            if (!item.isObject()) {
                fail("items[" + i + "] must be an object");
            }
            String path = "$.items[" + i + "]";
            String productId = requireTextField(item, "productId", path);
            validateKnownProduct(productId, context, path);
            validateRiskLevel(item.get("riskLevel"), analysisType, path);
            validateProductFacts(item, context.factsFor(productId), productId, path);
            validateSuggestedQuantity(item.get("suggestedQuantity"), analysisType, path);
        }
    }

    private void validateKnownProduct(String productId, AiOutputValidationContext context, String path) {
        if (context.knownProductIds().isEmpty()) {
            fail(path + ".productId cannot be validated without input product ids");
        }
        if (!context.knownProductIds().contains(productId)) {
            fail(path + ".productId " + productId + " is not in input snapshot");
        }
    }

    private void validateRiskLevel(JsonNode riskLevel, AiAnalysisType analysisType, String path) {
        if (riskLevel == null || riskLevel.isNull()) {
            if (analysisType == AiAnalysisType.REPLENISHMENT) {
                fail(path + ".riskLevel must not be blank");
            }
            return;
        }
        if (!riskLevel.isTextual() || riskLevel.asText().isBlank()) {
            fail(path + ".riskLevel must be text");
        }
        try {
            AiSuggestionRiskLevel.valueOf(riskLevel.asText().trim());
        } catch (IllegalArgumentException exception) {
            fail(path + ".riskLevel is not supported: " + riskLevel.asText());
        }
    }

    private void validateProductFacts(
            JsonNode item,
            AiOutputProductFacts facts,
            String productId,
            String path) {
        JsonNode productName = item.get("productName");
        if (productName != null && !productName.isNull()) {
            if (!productName.isTextual()) {
                fail(path + ".productName must be text");
            }
            if (facts == null || facts.productName() == null) {
                fail(path + ".productName for " + productId + " is not in input snapshot");
            }
            if (!facts.productName().equals(productName.asText())) {
                fail(path + ".productName for " + productId + " does not match input snapshot");
            }
        }

        for (String field : FACT_NUMERIC_FIELDS) {
            JsonNode value = item.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            long expected = expectedNumericFact(facts, productId, field, path);
            long actual = requireIntegralNonNegative(value, path + "." + field);
            if (actual != expected) {
                fail(path + "." + field + " for " + productId + " does not match input snapshot");
            }
        }
    }

    private long expectedNumericFact(
            AiOutputProductFacts facts,
            String productId,
            String field,
            String path) {
        if (facts == null) {
            fail(path + "." + field + " for " + productId + " is not in input snapshot");
        }
        Long value = switch (field) {
            case "availableStock" -> asLong(facts.availableStock());
            case "lockedStock" -> asLong(facts.lockedStock());
            case "safetyStock" -> asLong(facts.safetyStock());
            case "soldQuantityLast7Days" -> facts.soldQuantityLast7Days();
            default -> null;
        };
        if (value == null) {
            fail(path + "." + field + " for " + productId + " is not in input snapshot");
        }
        return value;
    }

    private void validateSuggestedQuantity(
            JsonNode suggestedQuantity,
            AiAnalysisType analysisType,
            String path) {
        if (suggestedQuantity == null || suggestedQuantity.isNull()) {
            if (analysisType == AiAnalysisType.REPLENISHMENT) {
                fail(path + ".suggestedQuantity must be a positive integer");
            }
            return;
        }
        long quantity = requireIntegralNonNegative(suggestedQuantity, path + ".suggestedQuantity");
        if (quantity <= 0) {
            fail(path + ".suggestedQuantity must be a positive integer");
        }
    }

    private void validateTimeRange(JsonNode timeRange, AiOutputValidationContext context) {
        if (timeRange == null || timeRange.isNull()) {
            return;
        }
        if (!timeRange.isObject()) {
            fail("timeRange must be an object");
        }
        validateTimeRangeValue(timeRange.get("from"), "timeRange.from", context);
        validateTimeRangeValue(timeRange.get("to"), "timeRange.to", context);
    }

    private void validateTimeRangeValue(JsonNode value, String field, AiOutputValidationContext context) {
        if (value == null || value.isNull()) {
            return;
        }
        if (!value.isTextual() || value.asText().isBlank()) {
            fail(field + " must be text");
        }
        String normalized = value.asText().trim();
        if (!context.allowedDateValues().isEmpty() && !context.allowedDateValues().contains(normalized)) {
            fail(field + " " + normalized + " is not in input snapshot");
        }
    }

    private void validateLimitations(JsonNode limitations) {
        if (limitations == null || limitations.isNull()) {
            return;
        }
        if (!limitations.isArray()) {
            fail("limitations must be an array");
        }
        for (int i = 0; i < limitations.size(); i++) {
            if (!limitations.get(i).isTextual()) {
                fail("limitations[" + i + "] must be text");
            }
        }
    }

    private void validateNumericValues(JsonNode node, String path) {
        if (node.isNumber() && node.decimalValue().compareTo(BigDecimal.ZERO) < 0) {
            fail(path + " must be non-negative");
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                validateNumericValues(field.getValue(), path + "." + field.getKey());
            }
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                validateNumericValues(node.get(i), path + "[" + i + "]");
            }
        }
    }

    private void validateForbiddenContent(JsonNode node, String path) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (FORBIDDEN_STOCK_CHANGE_FIELDS.contains(field.getKey())) {
                    fail(path + "." + field.getKey() + " is an unsupported stock-change claim");
                }
                validateForbiddenContent(field.getValue(), path + "." + field.getKey());
            }
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                validateForbiddenContent(node.get(i), path + "[" + i + "]");
            }
            return;
        }
        if (!node.isTextual()) {
            return;
        }
        String text = node.asText();
        if (SQL_PATTERN.matcher(text).find()) {
            fail("SQL content is not allowed");
        }
        if (INTERNAL_DETAIL_PATTERN.matcher(text).find()) {
            fail("internal endpoints, trusted headers, service ports, and secrets are not allowed");
        }
        if (STOCK_CHANGE_CLAIM_PATTERN.matcher(text).find()) {
            fail("stock-change claims are not allowed");
        }
    }

    private String requireTextField(JsonNode node, String field) {
        return requireTextField(node, field, "$");
    }

    private String requireTextField(JsonNode node, String field, String path) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            fail(path + "." + field + " must not be blank");
        }
        return value.asText().trim();
    }

    private long requireIntegralNonNegative(JsonNode value, String path) {
        if (!value.isIntegralNumber()) {
            fail(path + " must be an integer");
        }
        long number = value.asLong();
        if (number < 0) {
            fail(path + " must be non-negative");
        }
        return number;
    }

    private String canonicalJson(JsonNode root) {
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize validated AI output", exception);
        }
    }

    private Long asLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private void fail(String reason) {
        throw new AiOutputValidationException(reason);
    }
}
