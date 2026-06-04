package com.minimall.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.auth.context.AuthRole;
import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.core.audit.AdminAuditAction;
import com.minimall.common.core.audit.AdminAuditLogWriteRequest;
import com.minimall.common.core.audit.AdminAuditResourceType;
import com.minimall.common.core.audit.AdminAuditSourceType;
import com.minimall.common.core.audit.AdminAuditWriter;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.inventory.domain.AiOperationSuggestion;
import com.minimall.inventory.domain.AiOperationSuggestionItem;
import com.minimall.inventory.domain.AiOperationSuggestionSource;
import com.minimall.inventory.domain.AiOperationSuggestionStatus;
import com.minimall.inventory.domain.AiOperationSuggestionType;
import com.minimall.inventory.domain.AiSuggestionRiskLevel;
import com.minimall.inventory.domain.InboundOrder;
import com.minimall.inventory.domain.InboundOrderItem;
import com.minimall.inventory.domain.InboundOrderSource;
import com.minimall.inventory.domain.InboundOrderStatus;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.dto.RejectAiSuggestionRequest;
import com.minimall.inventory.repository.AiOperationSuggestionItemRepository;
import com.minimall.inventory.repository.AiOperationSuggestionRepository;
import com.minimall.inventory.repository.InboundOrderItemRepository;
import com.minimall.inventory.repository.InboundOrderRepository;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_ai_suggestion_controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "minimall.auth.jwt.secret=test-jwt-secret-for-admin-ai-suggestion-controller",
        "minimall.auth.jwt.expire-seconds=3600"
})
class AdminAiSuggestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AiOperationSuggestionRepository suggestionRepository;

    @Autowired
    private AiOperationSuggestionItemRepository itemRepository;

    @Autowired
    private InboundOrderRepository inboundOrderRepository;

    @Autowired
    private InboundOrderItemRepository inboundOrderItemRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryRecordRepository inventoryRecordRepository;

    @MockBean
    private AdminAuditWriter adminAuditWriter;

    @BeforeEach
    void setUp() {
        itemRepository.deleteAll();
        suggestionRepository.deleteAll();
        inboundOrderItemRepository.deleteAll();
        inboundOrderRepository.deleteAll();
        inventoryRecordRepository.deleteAll();
        inventoryRepository.deleteAll();
        reset(adminAuditWriter);
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/ai-suggestions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void listRejectsNonAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/ai-suggestions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void listReturnsPagedSuggestionsAndFiltersByStatus() throws Exception {
        saveSuggestion(
                "AIS-LIST-PENDING",
                AiOperationSuggestionStatus.PENDING_REVIEW,
                item("AIS-LIST-PENDING", "SKU-AIS-LIST-1", 4, AiSuggestionRiskLevel.HIGH));
        saveSuggestion(
                "AIS-LIST-REJECTED",
                AiOperationSuggestionStatus.REJECTED,
                item("AIS-LIST-REJECTED", "SKU-AIS-LIST-2", 6, AiSuggestionRiskLevel.LOW));

        mockMvc.perform(get("/api/admin/ai-suggestions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("status", "pending_review")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].suggestionNo").value("AIS-LIST-PENDING"))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.content[0].type").value("REPLENISHMENT"))
                .andExpect(jsonPath("$.data.content[0].source").value("AI_MODEL"))
                .andExpect(jsonPath("$.data.content[0].itemCount").value(1))
                .andExpect(jsonPath("$.data.content[0].totalSuggestedQuantity").value(4))
                .andExpect(jsonPath("$.data.content[0].items[0].productId").value("SKU-AIS-LIST-1"))
                .andExpect(jsonPath("$.data.content[0].items[0].riskLevel").value("HIGH"));

        verify(adminAuditWriter, never()).write(any());
    }

    @Test
    void listRejectsInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/admin/ai-suggestions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("status", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("Invalid AI suggestion status"));
    }

    @Test
    void detailReturnsSuggestionItemsAndAuditabilityFields() throws Exception {
        saveSuggestion(
                "AIS-DETAIL",
                AiOperationSuggestionStatus.PENDING_REVIEW,
                item("AIS-DETAIL", "SKU-AIS-DETAIL-1", 5, AiSuggestionRiskLevel.HIGH),
                item("AIS-DETAIL", "SKU-AIS-DETAIL-2", 3, AiSuggestionRiskLevel.MEDIUM));

        mockMvc.perform(get("/api/admin/ai-suggestions/AIS-DETAIL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.suggestionNo").value("AIS-DETAIL"))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.reason").value("Replenish AIS-DETAIL"))
                .andExpect(jsonPath("$.data.inputSnapshotRef").value("snapshot:AIS-DETAIL"))
                .andExpect(jsonPath("$.data.inputSummary").value("Structured low-stock summary for AIS-DETAIL"))
                .andExpect(jsonPath("$.data.linkedInboundNo").doesNotExist())
                .andExpect(jsonPath("$.data.rejectedReason").doesNotExist())
                .andExpect(jsonPath("$.data.reviewedByAdminUserId").doesNotExist())
                .andExpect(jsonPath("$.data.itemCount").value(2))
                .andExpect(jsonPath("$.data.totalSuggestedQuantity").value(8))
                .andExpect(jsonPath("$.data.items[0].productId").value("SKU-AIS-DETAIL-1"))
                .andExpect(jsonPath("$.data.items[0].productName").value("Name SKU-AIS-DETAIL-1"))
                .andExpect(jsonPath("$.data.items[0].availableStock").value(2))
                .andExpect(jsonPath("$.data.items[0].lockedStock").value(1))
                .andExpect(jsonPath("$.data.items[0].safetyStock").value(10))
                .andExpect(jsonPath("$.data.items[0].soldQuantityLast7Days").value(18))
                .andExpect(jsonPath("$.data.items[0].suggestedQuantity").value(5))
                .andExpect(jsonPath("$.data.items[0].riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.items[0].reason").value("Restock SKU-AIS-DETAIL-1"))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists());
    }

    @Test
    void detailReturnsNotFoundForUnknownSuggestionNo() throws Exception {
        mockMvc.perform(get("/api/admin/ai-suggestions/AIS-MISSING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("AI suggestion not found"));
    }

    @Test
    void rejectRequiresAuthenticationAndAdmin() throws Exception {
        saveSuggestion(
                "AIS-REJECT-AUTH",
                AiOperationSuggestionStatus.PENDING_REVIEW,
                item("AIS-REJECT-AUTH", "SKU-AIS-REJECT-AUTH", 2, AiSuggestionRiskLevel.MEDIUM));

        mockMvc.perform(post("/api/admin/ai-suggestions/AIS-REJECT-AUTH/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RejectAiSuggestionRequest("not needed"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        mockMvc.perform(post("/api/admin/ai-suggestions/AIS-REJECT-AUTH/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RejectAiSuggestionRequest("not allowed"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        assertThat(suggestionRepository.findBySuggestionNo("AIS-REJECT-AUTH"))
                .get()
                .extracting(AiOperationSuggestion::getStatus)
                .isEqualTo(AiOperationSuggestionStatus.PENDING_REVIEW);
        verify(adminAuditWriter, never()).write(any());
    }

    @Test
    void rejectPendingSuggestionCapturesReasonAndDoesNotMutateInventory() throws Exception {
        inventoryRepository.saveAndFlush(new Inventory("SKU-AIS-REJECT", 12, 2));
        saveSuggestion(
                "AIS-REJECT",
                AiOperationSuggestionStatus.PENDING_REVIEW,
                item("AIS-REJECT", "SKU-AIS-REJECT", 7, AiSuggestionRiskLevel.HIGH));
        long beforeInventoryCount = inventoryRepository.count();
        long beforeRecordCount = inventoryRecordRepository.count();

        mockMvc.perform(post("/api/admin/ai-suggestions/AIS-REJECT/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .header("X-Request-Id", "REQ-AIS-REJECT")
                        .header("X-Forwarded-For", "203.0.113.20")
                        .header(HttpHeaders.USER_AGENT, "AdminAiSuggestionTest/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RejectAiSuggestionRequest(" Supplier cannot deliver this week "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.suggestionNo").value("AIS-REJECT"))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectedReason").value("Supplier cannot deliver this week"))
                .andExpect(jsonPath("$.data.reviewedByAdminUserId").value(42))
                .andExpect(jsonPath("$.data.reviewedByAdminUsername").value("admin"))
                .andExpect(jsonPath("$.data.reviewedAt").exists())
                .andExpect(jsonPath("$.data.itemCount").value(1))
                .andExpect(jsonPath("$.data.totalSuggestedQuantity").value(7));

        assertThat(suggestionRepository.findBySuggestionNo("AIS-REJECT"))
                .get()
                .satisfies(suggestion -> {
                    assertThat(suggestion.getStatus()).isEqualTo(AiOperationSuggestionStatus.REJECTED);
                    assertThat(suggestion.getRejectedReason()).isEqualTo("Supplier cannot deliver this week");
                    assertThat(suggestion.getReviewedByAdminUserId()).isEqualTo(42L);
                    assertThat(suggestion.getReviewedByAdminUsername()).isEqualTo("admin");
                    assertThat(suggestion.getReviewedAt()).isNotNull();
                });
        assertThat(inventoryRepository.count()).isEqualTo(beforeInventoryCount);
        assertThat(inventoryRepository.findByProductId("SKU-AIS-REJECT"))
                .get()
                .satisfies(inventory -> {
                    assertThat(inventory.getAvailableStock()).isEqualTo(12);
                    assertThat(inventory.getLockedStock()).isEqualTo(2);
                });
        assertThat(inventoryRecordRepository.count()).isEqualTo(beforeRecordCount);

        ArgumentCaptor<AdminAuditLogWriteRequest> auditCaptor =
                ArgumentCaptor.forClass(AdminAuditLogWriteRequest.class);
        verify(adminAuditWriter, times(1)).write(auditCaptor.capture());
        AdminAuditLogWriteRequest audit = auditCaptor.getValue();
        assertThat(audit.adminUserId()).isEqualTo(42L);
        assertThat(audit.adminUsername()).isEqualTo("admin");
        assertThat(audit.action()).isEqualTo(AdminAuditAction.AI_SUGGESTION_REJECT);
        assertThat(audit.resourceType()).isEqualTo(AdminAuditResourceType.AI_SUGGESTION);
        assertThat(audit.resourceId()).isEqualTo("AIS-REJECT");
        assertThat(audit.referenceNo()).isEqualTo("AIS-REJECT");
        assertThat(audit.requestId()).isEqualTo("REQ-AIS-REJECT");
        assertThat(audit.sourceType()).isEqualTo(AdminAuditSourceType.AI_SUGGESTION);
        assertThat(audit.ip()).isEqualTo("203.0.113.20");
        assertThat(audit.userAgent()).isEqualTo("AdminAiSuggestionTest/1.0");
        assertThat(audit.beforeSnapshot().path("status").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(audit.afterSnapshot().path("status").asText()).isEqualTo("REJECTED");
        assertThat(audit.afterSnapshot().path("rejectedReason").asText())
                .isEqualTo("Supplier cannot deliver this week");
        assertThat(audit.summary()).contains(
                "Reject AI suggestion",
                "AIS-REJECT",
                "Supplier cannot deliver this week");
    }

    @Test
    void rejectAlreadyRejectedSuggestionIsRepeatSafe() throws Exception {
        saveSuggestion(
                "AIS-REJECT-REPEAT",
                AiOperationSuggestionStatus.REJECTED,
                item("AIS-REJECT-REPEAT", "SKU-AIS-REJECT-REPEAT", 1, AiSuggestionRiskLevel.LOW));

        mockMvc.perform(post("/api/admin/ai-suggestions/AIS-REJECT-REPEAT/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RejectAiSuggestionRequest("another reason"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectedReason").value("initial rejection"));

        verify(adminAuditWriter, never()).write(any());
    }

    @Test
    void rejectNonPendingSuggestionReturnsConflict() throws Exception {
        saveSuggestion(
                "AIS-REJECT-CONVERTED",
                AiOperationSuggestionStatus.CONVERTED_TO_DRAFT,
                item("AIS-REJECT-CONVERTED", "SKU-AIS-CONVERTED", 2, AiSuggestionRiskLevel.MEDIUM));

        mockMvc.perform(post("/api/admin/ai-suggestions/AIS-REJECT-CONVERTED/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RejectAiSuggestionRequest("too late"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()))
                .andExpect(jsonPath("$.message").value("Only pending AI suggestions can be rejected"));

        verify(adminAuditWriter, never()).write(any());
    }

    @Test
    void rejectRequiresReason() throws Exception {
        saveSuggestion(
                "AIS-REJECT-BLANK",
                AiOperationSuggestionStatus.PENDING_REVIEW,
                item("AIS-REJECT-BLANK", "SKU-AIS-BLANK", 1, AiSuggestionRiskLevel.LOW));

        mockMvc.perform(post("/api/admin/ai-suggestions/AIS-REJECT-BLANK/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RejectAiSuggestionRequest(" "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    void convertRequiresAuthenticationAndAdmin() throws Exception {
        saveSuggestion(
                "AIS-CONVERT-AUTH",
                AiOperationSuggestionStatus.PENDING_REVIEW,
                item("AIS-CONVERT-AUTH", "SKU-AIS-CONVERT-AUTH", 2, AiSuggestionRiskLevel.MEDIUM));

        mockMvc.perform(post("/api/admin/ai-suggestions/AIS-CONVERT-AUTH/convert-inbound-draft"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        mockMvc.perform(post("/api/admin/ai-suggestions/AIS-CONVERT-AUTH/convert-inbound-draft")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        assertThat(suggestionRepository.findBySuggestionNo("AIS-CONVERT-AUTH"))
                .get()
                .extracting(AiOperationSuggestion::getStatus)
                .isEqualTo(AiOperationSuggestionStatus.PENDING_REVIEW);
        assertThat(inboundOrderRepository.count()).isZero();
        assertThat(inboundOrderItemRepository.count()).isZero();
        verify(adminAuditWriter, never()).write(any());
    }

    @Test
    void convertPendingSuggestionCreatesInboundDraftAndDoesNotMutateInventory() throws Exception {
        inventoryRepository.saveAndFlush(new Inventory("SKU-AIS-CONVERT-1", 12, 2));
        saveSuggestion(
                "AIS-CONVERT",
                AiOperationSuggestionStatus.PENDING_REVIEW,
                item("AIS-CONVERT", "SKU-AIS-CONVERT-1", 7, AiSuggestionRiskLevel.HIGH),
                item("AIS-CONVERT", "SKU-AIS-CONVERT-2", 4, AiSuggestionRiskLevel.MEDIUM));
        long beforeInventoryCount = inventoryRepository.count();
        long beforeRecordCount = inventoryRecordRepository.count();

        MvcResult result = mockMvc.perform(post("/api/admin/ai-suggestions/AIS-CONVERT/convert-inbound-draft")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .header("X-Request-Id", "REQ-AIS-CONVERT")
                        .header("X-Forwarded-For", "203.0.113.21")
                        .header(HttpHeaders.USER_AGENT, "AdminAiSuggestionTest/2.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.suggestionNo").value("AIS-CONVERT"))
                .andExpect(jsonPath("$.data.status").value("CONVERTED_TO_DRAFT"))
                .andExpect(jsonPath("$.data.linkedInboundNo").exists())
                .andExpect(jsonPath("$.data.reviewedByAdminUserId").value(42))
                .andExpect(jsonPath("$.data.reviewedByAdminUsername").value("admin"))
                .andExpect(jsonPath("$.data.reviewedAt").exists())
                .andExpect(jsonPath("$.data.itemCount").value(2))
                .andExpect(jsonPath("$.data.totalSuggestedQuantity").value(11))
                .andExpect(jsonPath("$.data.items[0].productId").value("SKU-AIS-CONVERT-1"))
                .andExpect(jsonPath("$.data.items[0].suggestedQuantity").value(7))
                .andExpect(jsonPath("$.data.items[1].productId").value("SKU-AIS-CONVERT-2"))
                .andExpect(jsonPath("$.data.items[1].suggestedQuantity").value(4))
                .andReturn();

        String inboundNo = responseLinkedInboundNo(result);

        assertThat(suggestionRepository.findBySuggestionNo("AIS-CONVERT"))
                .get()
                .satisfies(suggestion -> {
                    assertThat(suggestion.getStatus()).isEqualTo(AiOperationSuggestionStatus.CONVERTED_TO_DRAFT);
                    assertThat(suggestion.getLinkedInboundNo()).isEqualTo(inboundNo);
                    assertThat(suggestion.getReviewedByAdminUserId()).isEqualTo(42L);
                    assertThat(suggestion.getReviewedByAdminUsername()).isEqualTo("admin");
                    assertThat(suggestion.getReviewedAt()).isNotNull();
                });
        assertThat(inboundOrderRepository.findByInboundNo(inboundNo))
                .get()
                .satisfies(order -> {
                    assertThat(order.getStatus()).isEqualTo(InboundOrderStatus.DRAFT);
                    assertThat(order.getSource()).isEqualTo(InboundOrderSource.AI_SUGGESTION);
                    assertThat(order.getCreatedByAdminUserId()).isEqualTo(42L);
                    assertThat(order.getCreatedByAdminUsername()).isEqualTo("admin");
                });
        assertThat(inboundOrderItemRepository.findByInboundNoOrderByIdAsc(inboundNo))
                .extracting(InboundOrderItem::getProductId)
                .containsExactly("SKU-AIS-CONVERT-1", "SKU-AIS-CONVERT-2");
        assertThat(inboundOrderItemRepository.findByInboundNoOrderByIdAsc(inboundNo))
                .extracting(InboundOrderItem::getQuantity)
                .containsExactly(7, 4);
        assertThat(inventoryRepository.count()).isEqualTo(beforeInventoryCount);
        assertThat(inventoryRepository.findByProductId("SKU-AIS-CONVERT-1"))
                .get()
                .satisfies(inventory -> {
                    assertThat(inventory.getAvailableStock()).isEqualTo(12);
                    assertThat(inventory.getLockedStock()).isEqualTo(2);
                });
        assertThat(inventoryRecordRepository.count()).isEqualTo(beforeRecordCount);

        ArgumentCaptor<AdminAuditLogWriteRequest> auditCaptor =
                ArgumentCaptor.forClass(AdminAuditLogWriteRequest.class);
        verify(adminAuditWriter, times(1)).write(auditCaptor.capture());
        AdminAuditLogWriteRequest audit = auditCaptor.getValue();
        assertThat(audit.adminUserId()).isEqualTo(42L);
        assertThat(audit.adminUsername()).isEqualTo("admin");
        assertThat(audit.action()).isEqualTo(AdminAuditAction.INBOUND_ORDER_CREATE);
        assertThat(audit.resourceType()).isEqualTo(AdminAuditResourceType.INBOUND_ORDER);
        assertThat(audit.resourceId()).isEqualTo(inboundNo);
        assertThat(audit.referenceNo()).isEqualTo("AIS-CONVERT");
        assertThat(audit.requestId()).isEqualTo("REQ-AIS-CONVERT");
        assertThat(audit.sourceType()).isEqualTo(AdminAuditSourceType.AI_SUGGESTION);
        assertThat(audit.ip()).isEqualTo("203.0.113.21");
        assertThat(audit.userAgent()).isEqualTo("AdminAiSuggestionTest/2.0");
        assertThat(audit.beforeSnapshot()).isNull();
        assertThat(audit.afterSnapshot().path("inboundNo").asText()).isEqualTo(inboundNo);
        assertThat(audit.afterSnapshot().path("source").asText()).isEqualTo("AI_SUGGESTION");
        assertThat(audit.afterSnapshot().path("status").asText()).isEqualTo("DRAFT");
        assertThat(audit.afterSnapshot().path("items").size()).isEqualTo(2);
        assertThat(audit.summary()).contains(
                "Create inbound order draft from AI suggestion",
                "AIS-CONVERT",
                inboundNo,
                "2 item(s)",
                "totalQuantity=11",
                "SKU-AIS-CONVERT-1 x7",
                "SKU-AIS-CONVERT-2 x4");
    }

    @Test
    void convertAlreadyConvertedSuggestionIsRepeatSafe() throws Exception {
        String inboundNo = "INB-AIS-CONVERT-REPEAT";
        saveInboundOrder(
                inboundNo,
                InboundOrderSource.AI_SUGGESTION,
                new InboundOrderItem(inboundNo, "SKU-AIS-CONVERT-REPEAT", 1));
        saveSuggestion(
                "AIS-CONVERT-REPEAT",
                AiOperationSuggestionStatus.CONVERTED_TO_DRAFT,
                item("AIS-CONVERT-REPEAT", "SKU-AIS-CONVERT-REPEAT", 1, AiSuggestionRiskLevel.LOW));
        long beforeInboundCount = inboundOrderRepository.count();
        long beforeInboundItemCount = inboundOrderItemRepository.count();

        mockMvc.perform(post("/api/admin/ai-suggestions/AIS-CONVERT-REPEAT/convert-inbound-draft")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONVERTED_TO_DRAFT"))
                .andExpect(jsonPath("$.data.linkedInboundNo").value(inboundNo));

        assertThat(inboundOrderRepository.count()).isEqualTo(beforeInboundCount);
        assertThat(inboundOrderItemRepository.count()).isEqualTo(beforeInboundItemCount);
        verify(adminAuditWriter, never()).write(any());
    }

    @Test
    void convertNonPendingSuggestionReturnsConflict() throws Exception {
        saveSuggestion(
                "AIS-CONVERT-REJECTED",
                AiOperationSuggestionStatus.REJECTED,
                item("AIS-CONVERT-REJECTED", "SKU-AIS-CONVERT-REJECTED", 3, AiSuggestionRiskLevel.LOW));

        mockMvc.perform(post("/api/admin/ai-suggestions/AIS-CONVERT-REJECTED/convert-inbound-draft")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()))
                .andExpect(jsonPath("$.message")
                        .value("Only pending AI suggestions can be converted to inbound draft"));

        assertThat(inboundOrderRepository.count()).isZero();
        assertThat(inboundOrderItemRepository.count()).isZero();
        verify(adminAuditWriter, never()).write(any());
    }

    @Test
    void convertSuggestionWithoutItemsReturnsConflict() throws Exception {
        saveSuggestion("AIS-CONVERT-EMPTY", AiOperationSuggestionStatus.PENDING_REVIEW);

        mockMvc.perform(post("/api/admin/ai-suggestions/AIS-CONVERT-EMPTY/convert-inbound-draft")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()))
                .andExpect(jsonPath("$.message").value("AI suggestion has no items to convert"));

        assertThat(inboundOrderRepository.count()).isZero();
        assertThat(inboundOrderItemRepository.count()).isZero();
        verify(adminAuditWriter, never()).write(any());
    }

    @Test
    void aiSuggestionEnumNamesRemainStable() {
        assertThat(AiOperationSuggestionStatus.values())
                .extracting(AiOperationSuggestionStatus::name)
                .containsExactly("PENDING_REVIEW", "CONVERTED_TO_DRAFT", "REJECTED", "APPLIED");
        assertThat(AdminAuditAction.valueOf("AI_SUGGESTION_REJECT"))
                .isEqualTo(AdminAuditAction.AI_SUGGESTION_REJECT);
        assertThat(AdminAuditResourceType.valueOf("AI_SUGGESTION"))
                .isEqualTo(AdminAuditResourceType.AI_SUGGESTION);
    }

    private void saveSuggestion(
            String suggestionNo,
            AiOperationSuggestionStatus status,
            AiOperationSuggestionItem... items) {
        AiOperationSuggestion suggestion = new AiOperationSuggestion(
                suggestionNo,
                AiOperationSuggestionType.REPLENISHMENT,
                AiOperationSuggestionSource.AI_MODEL,
                "Replenish " + suggestionNo,
                "snapshot:" + suggestionNo,
                "Structured low-stock summary for " + suggestionNo);
        if (status == AiOperationSuggestionStatus.REJECTED) {
            suggestion.reject("initial rejection", 1001L, "reviewer");
        } else if (status == AiOperationSuggestionStatus.CONVERTED_TO_DRAFT) {
            suggestion.convertToDraft("INB-" + suggestionNo, 1001L, "reviewer");
        } else if (status == AiOperationSuggestionStatus.APPLIED) {
            suggestion.convertToDraft("INB-" + suggestionNo, 1001L, "reviewer");
            suggestion.markApplied();
        }
        suggestionRepository.saveAndFlush(suggestion);
        itemRepository.saveAllAndFlush(List.of(items));
    }

    private void saveInboundOrder(
            String inboundNo,
            InboundOrderSource source,
            InboundOrderItem... items) {
        inboundOrderRepository.saveAndFlush(new InboundOrder(inboundNo, source, 42L, "admin"));
        inboundOrderItemRepository.saveAllAndFlush(List.of(items));
    }

    private AiOperationSuggestionItem item(
            String suggestionNo,
            String productId,
            int suggestedQuantity,
            AiSuggestionRiskLevel riskLevel) {
        return new AiOperationSuggestionItem(
                suggestionNo,
                productId,
                "Name " + productId,
                2,
                1,
                10,
                18,
                suggestedQuantity,
                riskLevel,
                "Restock " + productId);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String responseLinkedInboundNo(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("linkedInboundNo")
                .asText();
    }

    private String adminToken() {
        return jwtUtils.generateToken(42L, "admin", AuthRole.ADMIN);
    }

    private String userToken() {
        return jwtUtils.generateToken(43L, "alice", AuthRole.USER);
    }
}
