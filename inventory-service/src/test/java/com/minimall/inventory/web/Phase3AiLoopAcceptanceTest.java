package com.minimall.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
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
import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.ai.AiProvider;
import com.minimall.inventory.ai.MockAiProvider;
import com.minimall.inventory.client.order.SalesEvidenceClient;
import com.minimall.inventory.config.AiProviderProperties;
import com.minimall.inventory.domain.AiOperationSuggestionStatus;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryRecordSourceType;
import com.minimall.inventory.dto.AiSalesEvidenceResponse;
import com.minimall.inventory.repository.AiOperationSuggestionItemRepository;
import com.minimall.inventory.repository.AiOperationSuggestionRepository;
import com.minimall.inventory.repository.InboundOrderItemRepository;
import com.minimall.inventory.repository.InboundOrderRepository;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Task 13 acceptance regression: drives the entire allowed AI stock flow
 * through the real admin APIs and proves the locked boundaries along the way —
 * AI analysis/generation never mutates inventory, conversion only creates a
 * DRAFT, and stock changes exactly once at admin confirmation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:phase3_ai_loop_acceptance;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "minimall.auth.jwt.secret=test-jwt-secret-for-phase3-ai-loop-acceptance",
        "minimall.auth.jwt.expire-seconds=3600"
})
class Phase3AiLoopAcceptanceTest {

    private static final String PRODUCT_ID = "SKU-LOOP-1";
    private static final int INITIAL_AVAILABLE = 2;
    private static final int SAFETY_STOCK = 5;
    // MockAiProvider replenishes to double safety stock: 5 * 2 - 2 = 8.
    private static final int EXPECTED_SUGGESTED_QUANTITY = 8;

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
    private SalesEvidenceClient salesEvidenceClient;

    @MockBean
    private AdminAuditWriter adminAuditWriter;

    @MockBean
    private AiProvider aiProvider;

    @BeforeEach
    void setUp() {
        inboundOrderItemRepository.deleteAll();
        inboundOrderRepository.deleteAll();
        itemRepository.deleteAll();
        suggestionRepository.deleteAll();
        inventoryRecordRepository.deleteAll();
        inventoryRepository.deleteAll();
        reset(salesEvidenceClient, adminAuditWriter, aiProvider);

        MockAiProvider realMock = new MockAiProvider(new AiProviderProperties(), objectMapper);
        given(aiProvider.generate(any()))
                .willAnswer(invocation -> realMock.generate(invocation.getArgument(0)));
        given(salesEvidenceClient.salesByProduct(
                        eq(PRODUCT_ID), any(LocalDateTime.class), any(LocalDateTime.class), eq(PageRequest.of(0, 1))))
                .willReturn(page(List.of(new AiSalesEvidenceResponse(PRODUCT_ID, 9, 3, new BigDecimal("198.00")))));
        given(salesEvidenceClient.salesByProduct(
                        isNull(), any(LocalDateTime.class), any(LocalDateTime.class), any(PageRequest.class)))
                .willReturn(page(List.of()));

        Inventory inventory = new Inventory(PRODUCT_ID, INITIAL_AVAILABLE, 0);
        inventory.setSafetyStock(SAFETY_STOCK);
        inventoryRepository.saveAndFlush(inventory);
    }

    @Test
    void fullAiLoopAppliesStockExactlyOnceAtAdminConfirmation() throws Exception {
        // 1. Generate: persists one PENDING_REVIEW suggestion, no stock change.
        MvcResult generateResult = mockMvc.perform(
                        post("/api/admin/ai/replenishment-suggestions/generate")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.items[0].productId").value(PRODUCT_ID))
                .andExpect(jsonPath("$.data.items[0].suggestedQuantity").value(EXPECTED_SUGGESTED_QUANTITY))
                .andReturn();
        String suggestionNo = objectMapper.readTree(generateResult.getResponse().getContentAsString())
                .path("data").path("suggestionNo").asText();

        assertStockUnchanged();
        assertThat(inboundOrderRepository.count()).isZero();

        // 2. Convert: suggestion -> CONVERTED_TO_DRAFT, inbound DRAFT, still no stock change.
        MvcResult convertResult = mockMvc.perform(
                        post("/api/admin/ai-suggestions/" + suggestionNo + "/convert-inbound-draft")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONVERTED_TO_DRAFT"))
                .andReturn();
        String inboundNo = objectMapper.readTree(convertResult.getResponse().getContentAsString())
                .path("data").path("linkedInboundNo").asText();

        assertStockUnchanged();
        assertThat(inboundOrderRepository.findByInboundNo(inboundNo))
                .get()
                .satisfies(order -> assertThat(order.getStatus().name()).isEqualTo("DRAFT"));

        // 3. Confirm: the single stock mutation — inventory + record + audit + APPLIED sync.
        mockMvc.perform(post("/api/admin/inbound-orders/" + inboundNo + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .header("X-Request-Id", "REQ-LOOP-CONFIRM-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"));

        assertThat(inventoryRepository.findByProductId(PRODUCT_ID))
                .get()
                .satisfies(inventory -> assertThat(inventory.getAvailableStock())
                        .isEqualTo(INITIAL_AVAILABLE + EXPECTED_SUGGESTED_QUANTITY));
        assertThat(inventoryRecordRepository.findAll())
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.getSourceType()).isEqualTo(InventoryRecordSourceType.INBOUND_ORDER);
                    assertThat(record.getQuantity()).isEqualTo(EXPECTED_SUGGESTED_QUANTITY);
                    assertThat(record.getReferenceNo()).isEqualTo(inboundNo);
                });
        assertThat(suggestionRepository.findBySuggestionNo(suggestionNo))
                .get()
                .satisfies(suggestion ->
                        assertThat(suggestion.getStatus()).isEqualTo(AiOperationSuggestionStatus.APPLIED));

        ArgumentCaptor<AdminAuditLogWriteRequest> auditCaptor =
                ArgumentCaptor.forClass(AdminAuditLogWriteRequest.class);
        verify(adminAuditWriter, atLeastOnce()).write(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues())
                .extracting(AdminAuditLogWriteRequest::action)
                .contains(
                        AdminAuditAction.AI_SUGGESTION_CREATE,
                        AdminAuditAction.INBOUND_ORDER_CREATE,
                        AdminAuditAction.INBOUND_ORDER_CONFIRM,
                        AdminAuditAction.AI_SUGGESTION_APPLY);

        // 4. Repeat confirm with the same requestId: idempotent replay, no double stock.
        mockMvc.perform(post("/api/admin/inbound-orders/" + inboundNo + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .header("X-Request-Id", "REQ-LOOP-CONFIRM-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"));

        assertThat(inventoryRepository.findByProductId(PRODUCT_ID))
                .get()
                .satisfies(inventory -> assertThat(inventory.getAvailableStock())
                        .isEqualTo(INITIAL_AVAILABLE + EXPECTED_SUGGESTED_QUANTITY));
        assertThat(inventoryRecordRepository.count()).isEqualTo(1);
    }

    private void assertStockUnchanged() {
        assertThat(inventoryRepository.findByProductId(PRODUCT_ID))
                .get()
                .satisfies(inventory -> {
                    assertThat(inventory.getAvailableStock()).isEqualTo(INITIAL_AVAILABLE);
                    assertThat(inventory.getLockedStock()).isZero();
                });
        assertThat(inventoryRecordRepository.count()).isZero();
    }

    private PageResponse<AiSalesEvidenceResponse> page(List<AiSalesEvidenceResponse> content) {
        int totalPages = content.isEmpty() ? 0 : 1;
        return new PageResponse<>(content, 0, Math.max(1, content.size()), content.size(), totalPages);
    }

    private String adminToken() {
        return jwtUtils.generateToken(42L, "admin", AuthRole.ADMIN);
    }
}
