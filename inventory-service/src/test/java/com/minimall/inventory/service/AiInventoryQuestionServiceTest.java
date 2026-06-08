package com.minimall.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.inventory.client.order.SalesEvidenceClient;
import com.minimall.inventory.domain.AiInventoryEvidenceType;
import com.minimall.inventory.domain.AiInventoryQuestionIntent;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryRecordSourceType;
import com.minimall.inventory.dto.AiInventoryAskRequest;
import com.minimall.inventory.dto.AiInventoryAskResponse;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import({AiInventoryEvidenceFacade.class, AiInventoryQuestionService.class})
class AiInventoryQuestionServiceTest {

    @Autowired
    private AiInventoryQuestionService questionService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryRecordRepository inventoryRecordRepository;

    @MockBean
    private SalesEvidenceClient salesEvidenceClient;

    @BeforeEach
    void setUp() {
        inventoryRecordRepository.deleteAll();
        inventoryRepository.deleteAll();
    }

    @Test
    void answersCurrentStockWithReadOnlyEvidence() {
        inventoryRepository.saveAndFlush(inventory("SKU-QA-STOCK", 9, 2, 5));
        long inventoryCount = inventoryRepository.count();
        long recordCount = inventoryRecordRepository.count();

        AiInventoryAskResponse response = questionService.answer(new AiInventoryAskRequest(
                "What is the current stock for this SKU?",
                " SKU-QA-STOCK ",
                null,
                0));

        assertThat(response.intent()).isEqualTo(AiInventoryQuestionIntent.CURRENT_STOCK);
        assertThat(response.supported()).isTrue();
        assertThat(response.queryTime()).isNotNull();
        assertThat(response.answer()).contains("SKU-QA-STOCK", "9 available", "2 locked");
        assertThat(response.evidence().evidenceType()).isEqualTo(AiInventoryEvidenceType.CURRENT_INVENTORY);
        assertThat(response.evidence().inventories()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo("SKU-QA-STOCK");
            assertThat(item.availableStock()).isEqualTo(9);
            assertThat(item.lockedStock()).isEqualTo(2);
        });
        assertThat(response.limitations())
                .contains("Inventory Q&A is read-only and does not reserve, deduct, or adjust stock.");
        assertThat(inventoryRepository.count()).isEqualTo(inventoryCount);
        assertThat(inventoryRecordRepository.count()).isEqualTo(recordCount);
        then(salesEvidenceClient).should(never())
                .salesByProduct(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void answersLowStockListWithBackendComputedCandidates() {
        inventoryRepository.saveAndFlush(inventory("SKU-QA-LOW", 1, 0, 5));
        inventoryRepository.saveAndFlush(inventory("SKU-QA-OK", 20, 0, 5));

        AiInventoryAskResponse response = questionService.answer(new AiInventoryAskRequest(
                "List low stock products",
                null,
                10,
                0));

        assertThat(response.intent()).isEqualTo(AiInventoryQuestionIntent.LOW_STOCK_LIST);
        assertThat(response.supported()).isTrue();
        assertThat(response.answer()).contains("1 low-stock");
        assertThat(response.evidence().evidenceType()).isEqualTo(AiInventoryEvidenceType.LOW_STOCK_CANDIDATES);
        assertThat(response.evidence().inventories())
                .extracting(item -> item.productId())
                .containsExactly("SKU-QA-LOW");
        assertThat(response.evidence().records()).isEmpty();
    }

    @Test
    void answersProductStatusFromCurrentInventoryEvidence() {
        inventoryRepository.saveAndFlush(inventory("SKU-QA-STATUS", 0, 0, 3));

        AiInventoryAskResponse response = questionService.answer(new AiInventoryAskRequest(
                "Show health status for product",
                "SKU-QA-STATUS",
                null,
                0));

        assertThat(response.intent()).isEqualTo(AiInventoryQuestionIntent.PRODUCT_STATUS);
        assertThat(response.supported()).isTrue();
        assertThat(response.answer())
                .contains("SKU-QA-STATUS")
                .contains("ACTIVE")
                .contains("OUT_OF_STOCK")
                .contains("below safety stock");
    }

    @Test
    void answersRecentRecordsWithBoundedRecordEvidence() {
        inventoryRepository.saveAndFlush(inventory("SKU-QA-RECORDS", 12, 0, 5));
        inventoryRecordRepository.saveAndFlush(record("SKU-QA-RECORDS", "REQ-QA-1"));

        AiInventoryAskResponse response = questionService.answer(new AiInventoryAskRequest(
                "Explain recent inventory records",
                "SKU-QA-RECORDS",
                null,
                5));

        assertThat(response.intent()).isEqualTo(AiInventoryQuestionIntent.RECENT_RECORDS);
        assertThat(response.supported()).isTrue();
        assertThat(response.answer()).contains("1 recent inventory record");
        assertThat(response.evidence().records()).singleElement().satisfies(record -> {
            assertThat(record.productId()).isEqualTo("SKU-QA-RECORDS");
            assertThat(record.requestId()).isEqualTo("REQ-QA-1");
        });
        assertThat(response.limitations())
                .contains("Record evidence is bounded to recent records for the requested product.");
    }

    @Test
    void unsupportedQuestionReturnsControlledUnsupportedIntent() {
        AiInventoryAskResponse response = questionService.answer(new AiInventoryAskRequest(
                "Can you negotiate with suppliers?",
                null,
                null,
                null));

        assertThat(response.intent()).isEqualTo(AiInventoryQuestionIntent.UNSUPPORTED);
        assertThat(response.supported()).isFalse();
        assertThat(response.evidence()).isNull();
        assertThat(response.answer()).isEqualTo("Unsupported inventory question intent.");
        assertThat(response.limitations()).singleElement()
                .isEqualTo("Supported questions cover current stock, low-stock lists, product status, and recent records.");
    }

    @Test
    void productSpecificIntentRequiresProductId() {
        assertThatThrownBy(() -> questionService.answer(new AiInventoryAskRequest(
                        "What is the current stock?",
                        null,
                        null,
                        null)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo(
                            "productId is required for CURRENT_STOCK questions");
                });
    }

    @Test
    void blankQuestionUsesControlledValidationError() {
        assertThatThrownBy(() -> questionService.answer(new AiInventoryAskRequest(
                        " ",
                        null,
                        null,
                        null)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("question must not be blank");
                });
    }

    private Inventory inventory(String productId, int availableStock, int lockedStock, int safetyStock) {
        Inventory inventory = new Inventory(productId, availableStock, lockedStock);
        inventory.setSafetyStock(safetyStock);
        return inventory;
    }

    private InventoryRecord record(String productId, String requestId) {
        return new InventoryRecord(
                productId,
                null,
                InventoryChangeType.ADJUST_INCREASE,
                4,
                requestId,
                "Q&A test record",
                42L,
                "admin",
                InventoryRecordSourceType.ADMIN_ADJUSTMENT,
                requestId);
    }
}
