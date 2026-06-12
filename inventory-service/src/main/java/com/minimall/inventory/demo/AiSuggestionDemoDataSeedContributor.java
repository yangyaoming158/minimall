package com.minimall.inventory.demo;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(200)
public class AiSuggestionDemoDataSeedContributor implements DemoDataSeedContributor {

    static final String DEMO_ADMIN_USERNAME = "phase3-demo-admin";
    static final long DEMO_ADMIN_USER_ID = 9001L;

    private static final String MODEL_PROVIDER = "MOCK";
    private static final String PROMPT_VERSION = "replenishment-suggestion-v1";
    private static final String OUTPUT_SCHEMA_VERSION = "ai-replenishment-suggestion-v1";

    private static final List<DemoSuggestion> SUGGESTIONS = List.of(
            new DemoSuggestion(
                    "PH3-AI-SUG-PENDING",
                    "PENDING_REVIEW",
                    null,
                    null,
                    "Validated low-stock replenishment suggestion awaiting admin review.",
                    "PH3-AI-LOW-TEA",
                    "Phase 3 Low Stock Tea Set",
                    4,
                    1,
                    12,
                    4,
                    24,
                    "HIGH",
                    "Available stock is below safety stock and recent paid orders show continued demand.",
                    8),
            new DemoSuggestion(
                    "PH3-AI-SUG-REJECTED",
                    "REJECTED",
                    null,
                    "Campaign demand assumption was outdated; admin rejected the suggestion.",
                    "Rejected demo suggestion retained for history review.",
                    "PH3-AI-HOT-MUG",
                    "Phase 3 Hot Product Mug",
                    42,
                    3,
                    10,
                    18,
                    18,
                    "MEDIUM",
                    "Hot product demand was high, but the planned promotion was cancelled.",
                    7),
            new DemoSuggestion(
                    "PH3-AI-SUG-DRAFT",
                    "CONVERTED_TO_DRAFT",
                    "PH3-AI-INB-DRAFT",
                    null,
                    "Converted AI replenishment suggestion waiting as an inbound draft.",
                    "PH3-AI-LOW-TEA",
                    "Phase 3 Low Stock Tea Set",
                    4,
                    1,
                    12,
                    4,
                    20,
                    "HIGH",
                    "Admin converted this validated suggestion into a draft; stock is unchanged until confirmation.",
                    6),
            new DemoSuggestion(
                    "PH3-AI-SUG-APPLIED",
                    "APPLIED",
                    "PH3-AI-INB-APPLIED",
                    null,
                    "Converted AI suggestion with a linked inbound order already applied.",
                    "PH3-AI-STABLE-CUP",
                    "Phase 3 Stable Stock Cup",
                    96,
                    0,
                    20,
                    2,
                    16,
                    "LOW",
                    "Historical applied example for review and audit navigation.",
                    5));

    private static final List<DemoInboundOrder> INBOUND_ORDERS = List.of(
            new DemoInboundOrder(
                    "PH3-AI-INB-DRAFT",
                    "DRAFT",
                    null,
                    "PH3-AI-LOW-TEA",
                    20,
                    6),
            new DemoInboundOrder(
                    "PH3-AI-INB-APPLIED",
                    "APPLIED",
                    "PH3-AI-DEMO-CONFIRM-APPLIED",
                    "PH3-AI-STABLE-CUP",
                    16,
                    5));

    private final JdbcTemplate jdbcTemplate;

    public AiSuggestionDemoDataSeedContributor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void seed() {
        LocalDateTime seedTime = LocalDateTime.now().withNano(0);
        SUGGESTIONS.forEach(suggestion -> seedSuggestion(suggestion, seedTime));
        INBOUND_ORDERS.forEach(inboundOrder -> seedInboundOrder(inboundOrder, seedTime));
    }

    private void seedSuggestion(DemoSuggestion suggestion, LocalDateTime seedTime) {
        LocalDateTime createdAt = seedTime.minusHours(suggestion.hoursAgo());
        LocalDateTime reviewedAt = reviewedAt(suggestion, createdAt);
        String inputSummary = inputSummary(suggestion);
        String inputSnapshotJson = inputSnapshotJson(suggestion);
        String outputJson = outputJson(suggestion);

        if (exists("select count(*) from ai_operation_suggestion where suggestion_no = ?", suggestion.suggestionNo())) {
            jdbcTemplate.update("""
                    update ai_operation_suggestion
                       set type = ?,
                           status = ?,
                           source = ?,
                           reason = ?,
                           input_snapshot_ref = ?,
                           input_summary = ?,
                           model_provider = ?,
                           model_name = ?,
                           prompt_version = ?,
                           output_schema_version = ?,
                           validation_status = ?,
                           validation_error = null,
                           input_snapshot_json = ?,
                           validated_output_json = ?,
                           raw_model_output_json = ?,
                           linked_inbound_no = ?,
                           rejected_reason = ?,
                           reviewed_by_admin_user_id = ?,
                           reviewed_by_admin_username = ?,
                           reviewed_at = ?,
                           created_at = ?,
                           updated_at = ?
                     where suggestion_no = ?
                    """,
                    "REPLENISHMENT",
                    suggestion.status(),
                    "AI_MODEL",
                    suggestion.reason(),
                    "demo:" + suggestion.suggestionNo(),
                    inputSummary,
                    MODEL_PROVIDER,
                    null,
                    PROMPT_VERSION,
                    OUTPUT_SCHEMA_VERSION,
                    "VALID",
                    inputSnapshotJson,
                    outputJson,
                    outputJson,
                    suggestion.linkedInboundNo(),
                    suggestion.rejectedReason(),
                    reviewedAt == null ? null : DEMO_ADMIN_USER_ID,
                    reviewedAt == null ? null : DEMO_ADMIN_USERNAME,
                    timestamp(reviewedAt),
                    timestamp(createdAt),
                    timestamp(seedTime),
                    suggestion.suggestionNo());
        } else {
            jdbcTemplate.update("""
                    insert into ai_operation_suggestion (
                        suggestion_no, type, status, source, reason, input_snapshot_ref, input_summary,
                        model_provider, model_name, prompt_version, output_schema_version,
                        validation_status, validation_error, input_snapshot_json, validated_output_json,
                        raw_model_output_json, linked_inbound_no, rejected_reason, reviewed_by_admin_user_id,
                        reviewed_by_admin_username, reviewed_at, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    suggestion.suggestionNo(),
                    "REPLENISHMENT",
                    suggestion.status(),
                    "AI_MODEL",
                    suggestion.reason(),
                    "demo:" + suggestion.suggestionNo(),
                    inputSummary,
                    MODEL_PROVIDER,
                    null,
                    PROMPT_VERSION,
                    OUTPUT_SCHEMA_VERSION,
                    "VALID",
                    null,
                    inputSnapshotJson,
                    outputJson,
                    outputJson,
                    suggestion.linkedInboundNo(),
                    suggestion.rejectedReason(),
                    reviewedAt == null ? null : DEMO_ADMIN_USER_ID,
                    reviewedAt == null ? null : DEMO_ADMIN_USERNAME,
                    timestamp(reviewedAt),
                    timestamp(createdAt),
                    timestamp(seedTime));
        }

        seedSuggestionItem(suggestion, createdAt, seedTime);
    }

    private void seedSuggestionItem(
            DemoSuggestion suggestion,
            LocalDateTime createdAt,
            LocalDateTime seedTime) {
        if (exists("""
                select count(*) from ai_operation_suggestion_item
                 where suggestion_no = ?
                   and product_id = ?
                """,
                suggestion.suggestionNo(),
                suggestion.productId())) {
            jdbcTemplate.update("""
                    update ai_operation_suggestion_item
                       set product_name = ?,
                           available_stock = ?,
                           locked_stock = ?,
                           safety_stock = ?,
                           sold_quantity_last_7_days = ?,
                           suggested_quantity = ?,
                           risk_level = ?,
                           reason = ?,
                           created_at = ?,
                           updated_at = ?
                     where suggestion_no = ?
                       and product_id = ?
                    """,
                    suggestion.productName(),
                    suggestion.availableStock(),
                    suggestion.lockedStock(),
                    suggestion.safetyStock(),
                    suggestion.soldQuantityLast7Days(),
                    suggestion.suggestedQuantity(),
                    suggestion.riskLevel(),
                    suggestion.itemReason(),
                    timestamp(createdAt),
                    timestamp(seedTime),
                    suggestion.suggestionNo(),
                    suggestion.productId());
            return;
        }

        jdbcTemplate.update("""
                insert into ai_operation_suggestion_item (
                    suggestion_no, product_id, product_name, available_stock, locked_stock,
                    safety_stock, sold_quantity_last_7_days, suggested_quantity, risk_level,
                    reason, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                suggestion.suggestionNo(),
                suggestion.productId(),
                suggestion.productName(),
                suggestion.availableStock(),
                suggestion.lockedStock(),
                suggestion.safetyStock(),
                suggestion.soldQuantityLast7Days(),
                suggestion.suggestedQuantity(),
                suggestion.riskLevel(),
                suggestion.itemReason(),
                timestamp(createdAt),
                timestamp(seedTime));
    }

    private void seedInboundOrder(DemoInboundOrder inboundOrder, LocalDateTime seedTime) {
        LocalDateTime createdAt = seedTime.minusHours(inboundOrder.hoursAgo());
        LocalDateTime confirmedAt = "APPLIED".equals(inboundOrder.status()) ? createdAt.plusMinutes(30) : null;

        if (exists("select count(*) from inbound_order where inbound_no = ?", inboundOrder.inboundNo())) {
            jdbcTemplate.update("""
                    update inbound_order
                       set status = ?,
                           source = ?,
                           created_by_admin_user_id = ?,
                           created_by_admin_username = ?,
                           confirm_request_id = ?,
                           confirmed_by_admin_user_id = ?,
                           confirmed_by_admin_username = ?,
                           confirmed_at = ?,
                           created_at = ?,
                           updated_at = ?
                     where inbound_no = ?
                    """,
                    inboundOrder.status(),
                    "AI_SUGGESTION",
                    DEMO_ADMIN_USER_ID,
                    DEMO_ADMIN_USERNAME,
                    inboundOrder.confirmRequestId(),
                    confirmedAt == null ? null : DEMO_ADMIN_USER_ID,
                    confirmedAt == null ? null : DEMO_ADMIN_USERNAME,
                    timestamp(confirmedAt),
                    timestamp(createdAt),
                    timestamp(seedTime),
                    inboundOrder.inboundNo());
        } else {
            jdbcTemplate.update("""
                    insert into inbound_order (
                        inbound_no, status, source, created_by_admin_user_id, created_by_admin_username,
                        confirm_request_id, confirmed_by_admin_user_id, confirmed_by_admin_username,
                        confirmed_at, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    inboundOrder.inboundNo(),
                    inboundOrder.status(),
                    "AI_SUGGESTION",
                    DEMO_ADMIN_USER_ID,
                    DEMO_ADMIN_USERNAME,
                    inboundOrder.confirmRequestId(),
                    confirmedAt == null ? null : DEMO_ADMIN_USER_ID,
                    confirmedAt == null ? null : DEMO_ADMIN_USERNAME,
                    timestamp(confirmedAt),
                    timestamp(createdAt),
                    timestamp(seedTime));
        }

        seedInboundOrderItem(inboundOrder, createdAt, seedTime);
        if (confirmedAt != null) {
            seedAppliedInventoryRecord(inboundOrder, confirmedAt);
        }
    }

    private void seedInboundOrderItem(
            DemoInboundOrder inboundOrder,
            LocalDateTime createdAt,
            LocalDateTime seedTime) {
        if (exists("""
                select count(*) from inbound_order_item
                 where inbound_no = ?
                   and product_id = ?
                """,
                inboundOrder.inboundNo(),
                inboundOrder.productId())) {
            jdbcTemplate.update("""
                    update inbound_order_item
                       set quantity = ?,
                           created_at = ?,
                           updated_at = ?
                     where inbound_no = ?
                       and product_id = ?
                    """,
                    inboundOrder.quantity(),
                    timestamp(createdAt),
                    timestamp(seedTime),
                    inboundOrder.inboundNo(),
                    inboundOrder.productId());
            return;
        }

        jdbcTemplate.update("""
                insert into inbound_order_item (
                    inbound_no, product_id, quantity, created_at, updated_at
                ) values (?, ?, ?, ?, ?)
                """,
                inboundOrder.inboundNo(),
                inboundOrder.productId(),
                inboundOrder.quantity(),
                timestamp(createdAt),
                timestamp(seedTime));
    }

    private void seedAppliedInventoryRecord(DemoInboundOrder inboundOrder, LocalDateTime confirmedAt) {
        if (exists("""
                select count(*) from inventory_records
                 where source_type = ?
                   and request_id = ?
                   and product_id = ?
                """,
                "INBOUND_ORDER",
                inboundOrder.confirmRequestId(),
                inboundOrder.productId())) {
            jdbcTemplate.update("""
                    update inventory_records
                       set order_no = null,
                           change_type = ?,
                           quantity = ?,
                           reason = ?,
                           admin_user_id = ?,
                           admin_username = ?,
                           reference_no = ?,
                           status = ?,
                           created_at = ?,
                           updated_at = ?
                     where source_type = ?
                       and request_id = ?
                       and product_id = ?
                    """,
                    "ADJUST_INCREASE",
                    inboundOrder.quantity(),
                    "Phase 3 demo applied AI inbound order",
                    DEMO_ADMIN_USER_ID,
                    DEMO_ADMIN_USERNAME,
                    inboundOrder.inboundNo(),
                    "SUCCESS",
                    timestamp(confirmedAt),
                    timestamp(confirmedAt),
                    "INBOUND_ORDER",
                    inboundOrder.confirmRequestId(),
                    inboundOrder.productId());
            return;
        }

        jdbcTemplate.update("""
                insert into inventory_records (
                    product_id, order_no, request_id, change_type, source_type, quantity, reason,
                    admin_user_id, admin_username, reference_no, status, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                inboundOrder.productId(),
                null,
                inboundOrder.confirmRequestId(),
                "ADJUST_INCREASE",
                "INBOUND_ORDER",
                inboundOrder.quantity(),
                "Phase 3 demo applied AI inbound order",
                DEMO_ADMIN_USER_ID,
                DEMO_ADMIN_USERNAME,
                inboundOrder.inboundNo(),
                "SUCCESS",
                timestamp(confirmedAt),
                timestamp(confirmedAt));
    }

    private LocalDateTime reviewedAt(DemoSuggestion suggestion, LocalDateTime createdAt) {
        return "PENDING_REVIEW".equals(suggestion.status()) ? null : createdAt.plusMinutes(20);
    }

    private String inputSummary(DemoSuggestion suggestion) {
        return "Demo evidence for " + suggestion.productId()
                + ": availableStock=" + suggestion.availableStock()
                + ", lockedStock=" + suggestion.lockedStock()
                + ", safetyStock=" + suggestion.safetyStock()
                + ", soldQuantityLast7Days=" + suggestion.soldQuantityLast7Days();
    }

    private String inputSnapshotJson(DemoSuggestion suggestion) {
        return """
                {"source":"phase3-demo-data","productId":"%s","availableStock":%d,"lockedStock":%d,"safetyStock":%d,"soldQuantityLast7Days":%d}
                """.formatted(
                suggestion.productId(),
                suggestion.availableStock(),
                suggestion.lockedStock(),
                suggestion.safetyStock(),
                suggestion.soldQuantityLast7Days()).trim();
    }

    private String outputJson(DemoSuggestion suggestion) {
        return """
                {"analysisType":"REPLENISHMENT","summary":"%s","items":[{"productId":"%s","productName":"%s","suggestedQuantity":%d,"riskLevel":"%s","reason":"%s"}],"limitations":["Seeded demo data for local review workflow only."]}
                """.formatted(
                suggestion.reason(),
                suggestion.productId(),
                suggestion.productName(),
                suggestion.suggestedQuantity(),
                suggestion.riskLevel(),
                suggestion.itemReason()).trim();
    }

    private boolean exists(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    private Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private record DemoSuggestion(
            String suggestionNo,
            String status,
            String linkedInboundNo,
            String rejectedReason,
            String reason,
            String productId,
            String productName,
            int availableStock,
            int lockedStock,
            int safetyStock,
            int soldQuantityLast7Days,
            int suggestedQuantity,
            String riskLevel,
            String itemReason,
            int hoursAgo) {
    }

    private record DemoInboundOrder(
            String inboundNo,
            String status,
            String confirmRequestId,
            String productId,
            int quantity,
            int hoursAgo) {
    }
}
