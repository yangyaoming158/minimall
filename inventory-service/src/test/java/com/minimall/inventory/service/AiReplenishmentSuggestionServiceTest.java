package com.minimall.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.audit.AdminAuditAction;
import com.minimall.common.core.audit.AdminAuditLogWriteRequest;
import com.minimall.common.core.audit.AdminAuditWriter;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.ai.AiProvider;
import com.minimall.inventory.ai.AiProviderResponse;
import com.minimall.inventory.ai.AiProviderTokenUsage;
import com.minimall.inventory.ai.AiProviderType;
import com.minimall.inventory.ai.MockAiProvider;
import com.minimall.inventory.ai.validation.AiOutputValidationException;
import com.minimall.inventory.config.AiProviderProperties;
import com.minimall.inventory.client.order.SalesEvidenceClient;
import com.minimall.inventory.domain.AiOperationSuggestion;
import com.minimall.inventory.domain.AiOperationSuggestionSource;
import com.minimall.inventory.domain.AiOperationSuggestionStatus;
import com.minimall.inventory.domain.AiOperationSuggestionType;
import com.minimall.inventory.domain.AiSuggestionRiskLevel;
import com.minimall.inventory.domain.AiSuggestionValidationStatus;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.dto.AiSalesEvidenceResponse;
import com.minimall.inventory.dto.AiSuggestionResponse;
import com.minimall.inventory.repository.AiOperationSuggestionItemRepository;
import com.minimall.inventory.repository.AiOperationSuggestionRepository;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai_replenishment_suggestion_service;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "minimall.auth.jwt.secret=test-jwt-secret-for-ai-replenishment-service",
        "minimall.auth.jwt.expire-seconds=3600"
})
class AiReplenishmentSuggestionServiceTest {

    @Autowired
    private AiReplenishmentSuggestionService service;

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
    private ObjectMapper objectMapper;

    @MockBean
    private SalesEvidenceClient salesEvidenceClient;

    @MockBean
    private AdminAuditWriter adminAuditWriter;

    @MockBean
    private AiProvider aiProvider;

    @BeforeEach
    void setUp() {
        itemRepository.deleteAll();
        suggestionRepository.deleteAll();
        inventoryRecordRepository.deleteAll();
        inventoryRepository.deleteAll();
        reset(salesEvidenceClient, adminAuditWriter, aiProvider);
        givenHotSales(List.of(), 0);
    }

    @Test
    void generatePersistsValidatedPendingReviewSuggestion() {
        inventoryRepository.saveAndFlush(inventory("SKU-REPLENISH-1", 2, 1, 5));
        givenProductSales("SKU-REPLENISH-1", 9, 3, "198.00");
        delegateProviderToRealMock();
        long beforeInventoryCount = inventoryRepository.count();
        long beforeRecordCount = inventoryRecordRepository.count();

        AiSuggestionResponse response = service.generate(5, 1, auditContext());

        assertThat(response.suggestionNo()).startsWith("AIS-");
        assertThat(response.status()).isEqualTo(AiOperationSuggestionStatus.PENDING_REVIEW);
        assertThat(response.items()).hasSize(1);

        assertThat(suggestionRepository.count()).isEqualTo(1);
        AiOperationSuggestion saved = suggestionRepository
                .findBySuggestionNo(response.suggestionNo())
                .orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(AiOperationSuggestionStatus.PENDING_REVIEW);
        assertThat(saved.getType()).isEqualTo(AiOperationSuggestionType.REPLENISHMENT);
        assertThat(saved.getSource()).isEqualTo(AiOperationSuggestionSource.AI_MODEL);
        assertThat(saved.getModelProvider()).isEqualTo("MOCK");
        assertThat(saved.getModelName()).isEqualTo("mock-ai-provider");
        assertThat(saved.getPromptVersion()).isEqualTo("replenishment-suggestion-v2");
        assertThat(saved.getOutputSchemaVersion()).isEqualTo("inventory-analysis-output-v1");
        assertThat(saved.getValidationStatus()).isEqualTo(AiSuggestionValidationStatus.VALID);
        assertThat(saved.getValidationError()).isNull();
        assertThat(saved.getInputSnapshotJson()).contains("lowStockEvidence").contains("hotProductsEvidence");
        assertThat(saved.getValidatedOutputJson()).contains("SKU-REPLENISH-1");
        assertThat(saved.getRawModelOutputJson()).contains("SKU-REPLENISH-1");

        assertThat(itemRepository.findBySuggestionNoOrderByIdAsc(saved.getSuggestionNo()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getProductId()).isEqualTo("SKU-REPLENISH-1");
                    assertThat(item.getAvailableStock()).isEqualTo(2);
                    assertThat(item.getLockedStock()).isEqualTo(1);
                    assertThat(item.getSafetyStock()).isEqualTo(5);
                    assertThat(item.getSoldQuantityLast7Days()).isEqualTo(9);
                    // safetyStock * 2 - availableStock = 10 - 2 = 8
                    assertThat(item.getSuggestedQuantity()).isEqualTo(8);
                    assertThat(item.getRiskLevel()).isEqualTo(AiSuggestionRiskLevel.HIGH);
                    assertThat(item.getReason()).isNotBlank();
                });

        assertThat(inventoryRepository.count()).isEqualTo(beforeInventoryCount);
        assertThat(inventoryRecordRepository.count()).isEqualTo(beforeRecordCount);
        assertThat(inventoryRepository.findByProductId("SKU-REPLENISH-1"))
                .get()
                .satisfies(inventory -> {
                    assertThat(inventory.getAvailableStock()).isEqualTo(2);
                    assertThat(inventory.getLockedStock()).isEqualTo(1);
                });
        assertThat(inboundOrderRepository.count()).isZero();

        ArgumentCaptor<AdminAuditLogWriteRequest> auditCaptor =
                ArgumentCaptor.forClass(AdminAuditLogWriteRequest.class);
        verify(adminAuditWriter).write(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo(AdminAuditAction.AI_SUGGESTION_CREATE);
        assertThat(auditCaptor.getValue().resourceId()).isEqualTo(saved.getSuggestionNo());
    }

    @Test
    void generateRejectsWhenNoEvidenceWithoutCallingProvider() {
        assertThatThrownBy(() -> service.generate(5, 1, auditContext()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(aiProvider, never()).generate(any());
        assertThat(suggestionRepository.count()).isZero();
        assertThat(itemRepository.count()).isZero();
        verify(adminAuditWriter, never()).write(any());
    }

    @Test
    void generateRejectsInvalidModelOutputWithoutPersisting() {
        inventoryRepository.saveAndFlush(inventory("SKU-REPLENISH-2", 1, 0, 4));
        givenProductSales("SKU-REPLENISH-2", 4, 2, "88.00");
        givenProviderContent("""
                {"summary":"bad","analysisType":"REPLENISHMENT","items":[
                {"productId":"SKU-UNKNOWN","suggestedQuantity":3,"riskLevel":"HIGH"}],
                "limitations":[]}
                """);

        assertThatThrownBy(() -> service.generate(5, 1, auditContext()))
                .isInstanceOf(AiOutputValidationException.class);

        assertThat(suggestionRepository.count()).isZero();
        assertThat(itemRepository.count()).isZero();
        verify(adminAuditWriter, never()).write(any());
    }

    @Test
    void generateRejectsValidatedOutputWithoutItems() {
        inventoryRepository.saveAndFlush(inventory("SKU-REPLENISH-3", 1, 0, 4));
        givenProductSales("SKU-REPLENISH-3", 4, 2, "88.00");
        givenProviderContent("""
                {"summary":"nothing to do","analysisType":"REPLENISHMENT","items":[],"limitations":[]}
                """);

        assertThatThrownBy(() -> service.generate(5, 1, auditContext()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

        assertThat(suggestionRepository.count()).isZero();
        assertThat(itemRepository.count()).isZero();
        verify(adminAuditWriter, never()).write(any());
    }

    private void delegateProviderToRealMock() {
        MockAiProvider realMock = new MockAiProvider(new AiProviderProperties(), objectMapper);
        given(aiProvider.generate(any()))
                .willAnswer(invocation -> realMock.generate(invocation.getArgument(0)));
    }

    private void givenProviderContent(String content) {
        given(aiProvider.generate(any())).willReturn(new AiProviderResponse(
                AiProviderType.MOCK,
                "stub-model",
                content,
                AiProviderTokenUsage.empty(),
                "stub-request"));
    }

    private Inventory inventory(String productId, int availableStock, int lockedStock, int safetyStock) {
        Inventory inventory = new Inventory(productId, availableStock, lockedStock);
        inventory.setSafetyStock(safetyStock);
        return inventory;
    }

    private void givenProductSales(String productId, long soldQuantity, long orderCount, String totalAmount) {
        given(salesEvidenceClient.salesByProduct(
                        eq(productId), any(LocalDateTime.class), any(LocalDateTime.class), eq(PageRequest.of(0, 1))))
                .willReturn(page(List.of(new AiSalesEvidenceResponse(
                        productId, soldQuantity, orderCount, new BigDecimal(totalAmount))), 1));
    }

    private void givenHotSales(List<AiSalesEvidenceResponse> sales, long totalElements) {
        given(salesEvidenceClient.salesByProduct(
                        isNull(), any(LocalDateTime.class), any(LocalDateTime.class), any(PageRequest.class)))
                .willReturn(page(sales, totalElements));
    }

    private PageResponse<AiSalesEvidenceResponse> page(List<AiSalesEvidenceResponse> content, long totalElements) {
        int totalPages = totalElements == 0 ? 0 : 1;
        return new PageResponse<>(content, 0, Math.max(1, content.size()), totalElements, totalPages);
    }

    private InventoryAdminAuditContext auditContext() {
        return new InventoryAdminAuditContext(42L, "admin", "REQ-AI-GENERATE", "127.0.0.1", "JUnit");
    }
}
