package com.minimall.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.auth.context.AuthRole;
import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.core.audit.AdminAuditAction;
import com.minimall.common.core.audit.AdminAuditLogWriteRequest;
import com.minimall.common.core.audit.AdminAuditWriter;
import com.minimall.inventory.domain.AiOperationSuggestion;
import com.minimall.inventory.domain.AiOperationSuggestionItem;
import com.minimall.inventory.domain.AiOperationSuggestionStatus;
import com.minimall.inventory.domain.AiSuggestionRiskLevel;
import com.minimall.inventory.domain.Inventory;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai_suggestion_applied_sync;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "minimall.auth.jwt.secret=test-jwt-secret-for-ai-suggestion-applied-sync",
        "minimall.auth.jwt.expire-seconds=3600"
})
class AiSuggestionAppliedSyncTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryRecordRepository inventoryRecordRepository;

    @Autowired
    private AiOperationSuggestionRepository suggestionRepository;

    @Autowired
    private AiOperationSuggestionItemRepository itemRepository;

    @Autowired
    private InboundOrderRepository inboundOrderRepository;

    @Autowired
    private InboundOrderItemRepository inboundOrderItemRepository;

    @MockBean
    private AdminAuditWriter adminAuditWriter;

    @BeforeEach
    void setUp() {
        inboundOrderItemRepository.deleteAll();
        inboundOrderRepository.deleteAll();
        itemRepository.deleteAll();
        suggestionRepository.deleteAll();
        inventoryRecordRepository.deleteAll();
        inventoryRepository.deleteAll();
        reset(adminAuditWriter);
    }

    @Test
    void confirmingLinkedInboundOrderMarksSuggestionAppliedExactlyOnce() throws Exception {
        inventoryRepository.saveAndFlush(inventory("SKU-SYNC-1", 2, 0, 5));
        givenPendingSuggestion("AIS-SYNC-1", "SKU-SYNC-1", 8);

        MvcResult convertResult = mockMvc.perform(
                        post("/api/admin/ai-suggestions/AIS-SYNC-1/convert-inbound-draft")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONVERTED_TO_DRAFT"))
                .andExpect(jsonPath("$.data.linkedInboundNo").exists())
                .andReturn();
        String inboundNo = objectMapper.readTree(convertResult.getResponse().getContentAsString())
                .path("data").path("linkedInboundNo").asText();
        assertThat(suggestionStatus("AIS-SYNC-1")).isEqualTo(AiOperationSuggestionStatus.CONVERTED_TO_DRAFT);

        mockMvc.perform(post("/api/admin/inbound-orders/" + inboundNo + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .header("X-Request-Id", "REQ-SYNC-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"));

        assertThat(suggestionStatus("AIS-SYNC-1")).isEqualTo(AiOperationSuggestionStatus.APPLIED);
        assertThat(inventoryRepository.findByProductId("SKU-SYNC-1"))
                .get()
                .satisfies(inventory -> assertThat(inventory.getAvailableStock()).isEqualTo(10));

        ArgumentCaptor<AdminAuditLogWriteRequest> auditCaptor =
                ArgumentCaptor.forClass(AdminAuditLogWriteRequest.class);
        verify(adminAuditWriter, atLeastOnce()).write(auditCaptor.capture());
        List<AdminAuditAction> actions = auditCaptor.getAllValues().stream()
                .map(AdminAuditLogWriteRequest::action)
                .toList();
        assertThat(actions).contains(AdminAuditAction.INBOUND_ORDER_CONFIRM, AdminAuditAction.AI_SUGGESTION_APPLY);
        long applyAuditCount = actions.stream()
                .filter(AdminAuditAction.AI_SUGGESTION_APPLY::equals)
                .count();
        assertThat(applyAuditCount).isEqualTo(1);

        // Repeated confirmation with the same requestId replays the prior result:
        // stock is not applied twice and the suggestion stays APPLIED without a
        // second apply audit entry.
        reset(adminAuditWriter);
        mockMvc.perform(post("/api/admin/inbound-orders/" + inboundNo + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .header("X-Request-Id", "REQ-SYNC-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"));

        assertThat(suggestionStatus("AIS-SYNC-1")).isEqualTo(AiOperationSuggestionStatus.APPLIED);
        assertThat(inventoryRepository.findByProductId("SKU-SYNC-1"))
                .get()
                .satisfies(inventory -> assertThat(inventory.getAvailableStock()).isEqualTo(10));
        verify(adminAuditWriter, org.mockito.Mockito.never()).write(any());
    }

    @Test
    void confirmingManualInboundOrderDoesNotTouchSuggestions() throws Exception {
        inventoryRepository.saveAndFlush(inventory("SKU-SYNC-2", 3, 0, 5));
        givenPendingSuggestion("AIS-SYNC-UNRELATED", "SKU-SYNC-2", 4);

        MvcResult draftResult = mockMvc.perform(post("/api/admin/inbound-orders/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("{\"items\":[{\"productId\":\"SKU-SYNC-2\",\"quantity\":6}]}"))
                .andExpect(status().isOk())
                .andReturn();
        String inboundNo = objectMapper.readTree(draftResult.getResponse().getContentAsString())
                .path("data").path("inboundNo").asText();

        mockMvc.perform(post("/api/admin/inbound-orders/" + inboundNo + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .header("X-Request-Id", "REQ-SYNC-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"));

        assertThat(suggestionStatus("AIS-SYNC-UNRELATED")).isEqualTo(AiOperationSuggestionStatus.PENDING_REVIEW);

        ArgumentCaptor<AdminAuditLogWriteRequest> auditCaptor =
                ArgumentCaptor.forClass(AdminAuditLogWriteRequest.class);
        verify(adminAuditWriter, atLeastOnce()).write(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues())
                .extracting(AdminAuditLogWriteRequest::action)
                .doesNotContain(AdminAuditAction.AI_SUGGESTION_APPLY);
    }

    private AiOperationSuggestionStatus suggestionStatus(String suggestionNo) {
        return suggestionRepository.findBySuggestionNo(suggestionNo).orElseThrow().getStatus();
    }

    private void givenPendingSuggestion(String suggestionNo, String productId, int suggestedQuantity) {
        suggestionRepository.saveAndFlush(new AiOperationSuggestion(
                suggestionNo,
                null,
                null,
                "Replenish " + productId,
                "snapshot:" + suggestionNo,
                "Low-stock summary for " + productId));
        itemRepository.saveAndFlush(new AiOperationSuggestionItem(
                suggestionNo,
                productId,
                "Name " + productId,
                2,
                0,
                5,
                9,
                suggestedQuantity,
                AiSuggestionRiskLevel.HIGH,
                "Available stock is below safety stock."));
    }

    private Inventory inventory(String productId, int availableStock, int lockedStock, int safetyStock) {
        Inventory inventory = new Inventory(productId, availableStock, lockedStock);
        inventory.setSafetyStock(safetyStock);
        return inventory;
    }

    private String adminToken() {
        return jwtUtils.generateToken(42L, "admin", AuthRole.ADMIN);
    }
}
