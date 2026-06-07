package com.minimall.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.client.order.SalesEvidenceClient;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.inventory.domain.AiInventoryEvidenceType;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryRecordSourceType;
import com.minimall.inventory.dto.AiInventoryEvidenceResponse;
import com.minimall.inventory.dto.AiInventorySalesEvidenceResponse;
import com.minimall.inventory.dto.AiSalesEvidenceResponse;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import(AiInventoryEvidenceFacade.class)
class AiInventoryEvidenceFacadeTest {

    @Autowired
    private AiInventoryEvidenceFacade facade;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryRecordRepository inventoryRecordRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockBean
    private SalesEvidenceClient salesEvidenceClient;

    @BeforeEach
    void setUp() {
        inventoryRecordRepository.deleteAll();
        inventoryRepository.deleteAll();
    }

    @Test
    void currentInventoryReturnsStockAndRecentRecordEvidenceWithoutMutation() {
        Inventory inventory = inventoryRepository.saveAndFlush(inventory("SKU-AI-EVIDENCE", 6, 2, 8));
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 1, 9, 0);
        touchInventory(inventory.getId(), createdAt, createdAt.plusHours(1));
        saveRecord("SKU-AI-EVIDENCE", InventoryChangeType.ADJUST_INCREASE, 10, "REQ-OLD",
                createdAt.plusMinutes(10));
        saveRecord("SKU-AI-EVIDENCE", InventoryChangeType.DEDUCT, 4, "ORDER-NEW",
                createdAt.plusMinutes(40));
        long inventoryCount = inventoryRepository.count();
        long recordCount = inventoryRecordRepository.count();

        AiInventoryEvidenceResponse response = facade.currentInventory("SKU-AI-EVIDENCE", 1);

        assertThat(response.evidenceType()).isEqualTo(AiInventoryEvidenceType.CURRENT_INVENTORY);
        assertThat(response.generatedAt()).isNotNull();
        assertThat(response.dataFrom()).isEqualTo(createdAt);
        assertThat(response.dataTo()).isEqualTo(createdAt.plusHours(1));
        assertThat(response.inventories()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo("SKU-AI-EVIDENCE");
            assertThat(item.availableStock()).isEqualTo(6);
            assertThat(item.lockedStock()).isEqualTo(2);
            assertThat(item.safetyStock()).isEqualTo(8);
            assertThat(item.lowStock()).isTrue();
            assertThat(item.stockState().name()).isEqualTo("IN_STOCK");
        });
        assertThat(response.records()).singleElement().satisfies(record -> {
            assertThat(record.productId()).isEqualTo("SKU-AI-EVIDENCE");
            assertThat(record.requestId()).isEqualTo("ORDER-NEW");
            assertThat(record.changeType()).isEqualTo(InventoryChangeType.DEDUCT);
            assertThat(record.quantity()).isEqualTo(4);
        });
        assertThat(inventoryRepository.count()).isEqualTo(inventoryCount);
        assertThat(inventoryRecordRepository.count()).isEqualTo(recordCount);
        assertThat(inventoryRepository.findByProductId("SKU-AI-EVIDENCE"))
                .get()
                .extracting(Inventory::getAvailableStock)
                .isEqualTo(6);
    }

    @Test
    void currentInventoryMissingProductUsesBusinessException() {
        assertThatThrownBy(() -> facade.currentInventory("SKU-MISSING", 3))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    assertThat(exception.getMessage()).isEqualTo("Inventory not found");
                });
    }

    @Test
    void lowStockCandidatesReturnsOnlyBackendComputedLowStockWithBoundedRecords() {
        Inventory firstLow = inventoryRepository.saveAndFlush(inventory("SKU-LOW-A", 1, 0, 5));
        Inventory secondLow = inventoryRepository.saveAndFlush(inventory("SKU-LOW-B", 0, 1, 2));
        inventoryRepository.saveAndFlush(inventory("SKU-HEALTHY", 20, 0, 5));
        touchInventory(firstLow.getId(), LocalDateTime.of(2026, 6, 2, 8, 0),
                LocalDateTime.of(2026, 6, 2, 8, 5));
        touchInventory(secondLow.getId(), LocalDateTime.of(2026, 6, 3, 8, 0),
                LocalDateTime.of(2026, 6, 3, 8, 5));
        saveRecord("SKU-LOW-A", InventoryChangeType.DEDUCT, 2, "ORDER-A-1",
                LocalDateTime.of(2026, 6, 2, 9, 0));
        saveRecord("SKU-LOW-A", InventoryChangeType.RELEASE, 1, "ORDER-A-2",
                LocalDateTime.of(2026, 6, 2, 10, 0));
        saveRecord("SKU-LOW-B", InventoryChangeType.ADJUST_INCREASE, 3, "REQ-B-1",
                LocalDateTime.of(2026, 6, 3, 9, 0));

        AiInventoryEvidenceResponse response = facade.lowStockCandidates(10, 1);

        assertThat(response.evidenceType()).isEqualTo(AiInventoryEvidenceType.LOW_STOCK_CANDIDATES);
        assertThat(response.inventories())
                .extracting(item -> item.productId())
                .containsExactly("SKU-LOW-A", "SKU-LOW-B");
        assertThat(response.inventories()).allMatch(item -> item.lowStock());
        assertThat(response.records())
                .extracting(record -> record.requestId())
                .containsExactlyInAnyOrder("ORDER-A-2", "REQ-B-1");
        assertThat(response.dataFrom()).isEqualTo(LocalDateTime.of(2026, 6, 2, 8, 0));
        assertThat(response.dataTo()).isEqualTo(LocalDateTime.of(2026, 6, 3, 9, 0));
    }

    @Test
    void limitsLowStockCandidatesAndRecordEvidence() {
        for (int i = 0; i < 3; i++) {
            inventoryRepository.saveAndFlush(inventory("SKU-LIMIT-" + i, 0, 0, 3));
            saveRecord("SKU-LIMIT-" + i, InventoryChangeType.DEDUCT, 1, "ORDER-LIMIT-" + i,
                    LocalDateTime.of(2026, 6, 4, 8, i));
        }

        AiInventoryEvidenceResponse response = facade.lowStockCandidates(2, 0);

        assertThat(response.inventories()).hasSize(2);
        assertThat(response.records()).isEmpty();
        assertThat(response.inventories())
                .extracting(item -> item.productId())
                .containsExactly("SKU-LIMIT-0", "SKU-LIMIT-1");
    }

    @Test
    void responseCollectionsAreImmutable() {
        inventoryRepository.saveAndFlush(inventory("SKU-IMMUTABLE", 0, 0, 1));

        AiInventoryEvidenceResponse response = facade.lowStockCandidates(10, 0);

        assertThatThrownBy(() -> response.inventories().add(response.inventories().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> response.records().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void lowStockAnalysisEvidenceCombinesLowStockInventoryWithSevenDaySalesEvidence() {
        Inventory firstLow = inventoryRepository.saveAndFlush(inventory("SKU-LOW-AI-A", 2, 1, 5));
        Inventory secondLow = inventoryRepository.saveAndFlush(inventory("SKU-LOW-AI-B", 0, 0, 3));
        inventoryRepository.saveAndFlush(inventory("SKU-AI-HEALTHY", 30, 0, 5));
        touchInventory(firstLow.getId(), LocalDateTime.of(2026, 6, 5, 9, 0),
                LocalDateTime.of(2026, 6, 5, 9, 10));
        touchInventory(secondLow.getId(), LocalDateTime.of(2026, 6, 5, 10, 0),
                LocalDateTime.of(2026, 6, 5, 10, 10));
        saveRecord("SKU-LOW-AI-A", InventoryChangeType.DEDUCT, 3, "ORDER-AI-A",
                LocalDateTime.of(2026, 6, 6, 8, 0));
        given(salesEvidenceClient.salesByProduct(
                        eq("SKU-LOW-AI-A"), any(LocalDateTime.class), any(LocalDateTime.class),
                        eq(PageRequest.of(0, 1))))
                .willReturn(page(List.of(sales("SKU-LOW-AI-A", 6, 2, "168.00")), 1));
        given(salesEvidenceClient.salesByProduct(
                        eq("SKU-LOW-AI-B"), any(LocalDateTime.class), any(LocalDateTime.class),
                        eq(PageRequest.of(0, 1))))
                .willReturn(page(List.of(), 0));
        long inventoryCount = inventoryRepository.count();
        long recordCount = inventoryRecordRepository.count();

        AiInventorySalesEvidenceResponse response = facade.lowStockAnalysisEvidence(10, 1);

        assertThat(response.evidenceType()).isEqualTo(AiInventoryEvidenceType.LOW_STOCK_ANALYSIS);
        assertThat(response.days()).isEqualTo(7);
        assertThat(response.dataFrom()).isEqualTo(response.generatedAt().minusDays(7));
        assertThat(response.dataTo()).isEqualTo(response.generatedAt());
        assertThat(response.limitations())
                .contains("Sales evidence is limited to paid orders in the selected window.");
        assertThat(response.products())
                .extracting(product -> product.productId())
                .containsExactly("SKU-LOW-AI-A", "SKU-LOW-AI-B");
        assertThat(response.products().get(0)).satisfies(product -> {
            assertThat(product.rank()).isEqualTo(1);
            assertThat(product.inventory().lowStock()).isTrue();
            assertThat(product.sales().soldQuantity()).isEqualTo(6);
            assertThat(product.sales().orderCount()).isEqualTo(2);
            assertThat(product.records()).singleElement()
                    .extracting(record -> record.requestId())
                    .isEqualTo("ORDER-AI-A");
            assertThat(product.limitations()).isEmpty();
        });
        assertThat(response.products().get(1)).satisfies(product -> {
            assertThat(product.rank()).isEqualTo(2);
            assertThat(product.sales().soldQuantity()).isZero();
            assertThat(product.sales().orderCount()).isZero();
            assertThat(product.limitations())
                    .contains("No paid sales evidence found in selected window.")
                    .contains("No recent inventory record evidence found.");
        });
        assertThat(inventoryRepository.count()).isEqualTo(inventoryCount);
        assertThat(inventoryRecordRepository.count()).isEqualTo(recordCount);
    }

    @Test
    void hotProductsEvidenceUsesSupportedWindowAndJoinsCurrentInventoryWhenAvailable() {
        Inventory hotInventory = inventoryRepository.saveAndFlush(inventory("SKU-HOT-AI-A", 4, 0, 10));
        touchInventory(hotInventory.getId(), LocalDateTime.of(2026, 6, 1, 8, 0),
                LocalDateTime.of(2026, 6, 1, 8, 10));
        saveRecord("SKU-HOT-AI-A", InventoryChangeType.ADJUST_INCREASE, 12, "REQ-HOT-A",
                LocalDateTime.of(2026, 6, 2, 8, 0));
        given(salesEvidenceClient.salesByProduct(
                        isNull(), any(LocalDateTime.class), any(LocalDateTime.class),
                        eq(PageRequest.of(0, 2))))
                .willReturn(page(List.of(
                        sales("SKU-HOT-AI-A", 20, 5, "560.00"),
                        sales("SKU-HOT-MISSING", 9, 3, "270.00")), 3));
        long inventoryCount = inventoryRepository.count();
        long recordCount = inventoryRecordRepository.count();

        AiInventorySalesEvidenceResponse response = facade.hotProductsEvidence(30, 2, 1);

        assertThat(response.evidenceType()).isEqualTo(AiInventoryEvidenceType.HOT_PRODUCTS);
        assertThat(response.days()).isEqualTo(30);
        assertThat(response.dataFrom()).isEqualTo(response.generatedAt().minusDays(30));
        assertThat(response.dataTo()).isEqualTo(response.generatedAt());
        assertThat(response.limitations())
                .contains("Hot-product sales evidence is limited to the first 2 products.");
        assertThat(response.products())
                .extracting(product -> product.productId())
                .containsExactly("SKU-HOT-AI-A", "SKU-HOT-MISSING");
        assertThat(response.products().get(0)).satisfies(product -> {
            assertThat(product.rank()).isEqualTo(1);
            assertThat(product.inventory()).isNotNull();
            assertThat(product.inventory().lowStock()).isTrue();
            assertThat(product.sales().soldQuantity()).isEqualTo(20);
            assertThat(product.records()).singleElement()
                    .extracting(record -> record.requestId())
                    .isEqualTo("REQ-HOT-A");
            assertThat(product.limitations()).isEmpty();
        });
        assertThat(response.products().get(1)).satisfies(product -> {
            assertThat(product.rank()).isEqualTo(2);
            assertThat(product.inventory()).isNull();
            assertThat(product.sales().orderCount()).isEqualTo(3);
            assertThat(product.limitations())
                    .contains("Current inventory evidence is unavailable for this product.")
                    .contains("No recent inventory record evidence found.");
        });
        assertThat(inventoryRepository.count()).isEqualTo(inventoryCount);
        assertThat(inventoryRecordRepository.count()).isEqualTo(recordCount);
    }

    @Test
    void hotProductsEvidenceRejectsUnsupportedDaysBeforeCallingSalesClient() {
        assertThatThrownBy(() -> facade.hotProductsEvidence(14, 10, 1))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("hot product evidence days must be 7 or 30");
                });
        then(salesEvidenceClient).should(never())
                .salesByProduct(any(), any(), any(), any());
    }

    private Inventory inventory(String productId, int availableStock, int lockedStock, int safetyStock) {
        Inventory inventory = new Inventory(productId, availableStock, lockedStock);
        inventory.setSafetyStock(safetyStock);
        return inventory;
    }

    private void saveRecord(
            String productId,
            InventoryChangeType changeType,
            int quantity,
            String referenceNo,
            LocalDateTime createdAt) {
        InventoryRecordSourceType sourceType = switch (changeType) {
            case DEDUCT -> InventoryRecordSourceType.ORDER_DEDUCT;
            case RELEASE -> InventoryRecordSourceType.ORDER_RELEASE;
            case ADJUST_INCREASE, ADJUST_DECREASE -> InventoryRecordSourceType.ADMIN_ADJUSTMENT;
        };
        String orderNo = sourceType == InventoryRecordSourceType.ORDER_DEDUCT
                || sourceType == InventoryRecordSourceType.ORDER_RELEASE ? referenceNo : null;
        InventoryRecord saved = inventoryRecordRepository.saveAndFlush(new InventoryRecord(
                productId,
                orderNo,
                changeType,
                quantity,
                referenceNo,
                "AI evidence test",
                42L,
                "admin",
                sourceType,
                referenceNo));
        jdbcTemplate.update(
                "update inventory_records set created_at = ?, updated_at = ? where id = ?",
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt),
                saved.getId());
        entityManager.clear();
    }

    private void touchInventory(Long id, LocalDateTime createdAt, LocalDateTime updatedAt) {
        jdbcTemplate.update(
                "update inventory set created_at = ?, updated_at = ? where id = ?",
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(updatedAt),
                id);
        entityManager.clear();
    }

    private AiSalesEvidenceResponse sales(String productId, long soldQuantity, long orderCount, String totalAmount) {
        return new AiSalesEvidenceResponse(productId, soldQuantity, orderCount, new BigDecimal(totalAmount));
    }

    private PageResponse<AiSalesEvidenceResponse> page(List<AiSalesEvidenceResponse> content, long totalElements) {
        int totalPages = totalElements == 0 ? 0 : 1;
        return new PageResponse<>(content, 0, Math.max(1, content.size()), totalElements, totalPages);
    }
}
