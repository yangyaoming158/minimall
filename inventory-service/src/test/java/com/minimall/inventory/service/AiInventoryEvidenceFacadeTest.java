package com.minimall.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.inventory.domain.AiInventoryEvidenceType;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryRecordSourceType;
import com.minimall.inventory.dto.AiInventoryEvidenceResponse;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
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
}
