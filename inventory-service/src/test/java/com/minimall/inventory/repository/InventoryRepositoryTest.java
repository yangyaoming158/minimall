package com.minimall.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class InventoryRepositoryTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Test
    void savesInventoryAndFindsByProductId() {
        Inventory saved = inventoryRepository.saveAndFlush(new Inventory("SKU-INV-1001", 100, 5));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(inventoryRepository.findByProductId("SKU-INV-1001"))
                .isPresent()
                .get()
                .satisfies(inventory -> {
                    assertThat(inventory.getAvailableStock()).isEqualTo(100);
                    assertThat(inventory.getLockedStock()).isEqualTo(5);
                    assertThat(inventory.getStatus()).isEqualTo(InventoryStatus.ACTIVE);
                });
        assertThat(inventoryRepository.existsByProductId("SKU-INV-1001")).isTrue();
    }

    @Test
    void persistsStatusAsEnumValue() {
        Inventory inventory = new Inventory("SKU-INV-1002", 10, 0);
        inventory.setStatus(InventoryStatus.INACTIVE);
        inventoryRepository.saveAndFlush(inventory);

        assertThat(inventoryRepository.findByProductId("SKU-INV-1002"))
                .isPresent()
                .get()
                .extracting(Inventory::getStatus)
                .isEqualTo(InventoryStatus.INACTIVE);
    }

    @Test
    void duplicateProductIdViolatesUniqueConstraint() {
        inventoryRepository.saveAndFlush(new Inventory("SKU-INV-DUP", 10, 0));

        assertThatThrownBy(() -> inventoryRepository.saveAndFlush(new Inventory("SKU-INV-DUP", 20, 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void safetyStockDefaultsToZeroAndPersists() {
        inventoryRepository.saveAndFlush(new Inventory("SKU-INV-SAFE-1", 50, 0));

        Inventory withThreshold = new Inventory("SKU-INV-SAFE-2", 50, 0);
        withThreshold.setSafetyStock(15);
        inventoryRepository.saveAndFlush(withThreshold);

        assertThat(inventoryRepository.findByProductId("SKU-INV-SAFE-1"))
                .get()
                .extracting(Inventory::getSafetyStock)
                .isEqualTo(0);
        assertThat(inventoryRepository.findByProductId("SKU-INV-SAFE-2"))
                .get()
                .extracting(Inventory::getSafetyStock)
                .isEqualTo(15);
    }

    @Test
    void findLowStockReturnsOnlyActiveProductsAtOrBelowPositiveThreshold() {
        // At threshold -> low stock.
        Inventory atThreshold = new Inventory("SKU-LOW-AT", 5, 0);
        atThreshold.setSafetyStock(5);
        // Below threshold -> low stock.
        Inventory belowThreshold = new Inventory("SKU-LOW-BELOW", 2, 0);
        belowThreshold.setSafetyStock(10);
        // Above threshold -> healthy.
        Inventory aboveThreshold = new Inventory("SKU-HEALTHY", 20, 0);
        aboveThreshold.setSafetyStock(5);
        // Threshold disabled (0) -> never low stock even at zero available.
        Inventory thresholdDisabled = new Inventory("SKU-NO-THRESHOLD", 0, 0);
        // Inactive product at/below threshold -> excluded.
        Inventory inactive = new Inventory("SKU-INACTIVE", 1, 0);
        inactive.setSafetyStock(5);
        inactive.setStatus(InventoryStatus.INACTIVE);

        inventoryRepository.saveAll(List.of(
                atThreshold, belowThreshold, aboveThreshold, thresholdDisabled, inactive));
        inventoryRepository.flush();

        Page<Inventory> lowStock = inventoryRepository.findLowStock(InventoryStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(lowStock.getContent())
                .extracting(Inventory::getProductId)
                .containsExactlyInAnyOrder("SKU-LOW-AT", "SKU-LOW-BELOW");
        assertThat(lowStock.getContent())
                .allMatch(Inventory::isLowStock);
    }

    @Test
    void findLowStockIsPaginated() {
        for (int i = 0; i < 3; i++) {
            Inventory low = new Inventory("SKU-PAGE-" + i, 0, 0);
            low.setSafetyStock(5);
            inventoryRepository.save(low);
        }
        inventoryRepository.flush();

        Page<Inventory> firstPage = inventoryRepository.findLowStock(InventoryStatus.ACTIVE, PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(2);
    }

    @Test
    void savingStaleVersionFailsOptimisticLock() {
        Inventory persisted = testEntityManager.persistFlushFind(new Inventory("SKU-VER", 10, 0));
        assertThat(persisted.getVersion()).isEqualTo(0L);
        Long id = persisted.getId();

        // Load a stale snapshot, then detach it so a later write does not share its identity.
        testEntityManager.clear();
        Inventory stale = testEntityManager.find(Inventory.class, id);
        testEntityManager.clear();

        // A separate update bumps the persisted version to 1.
        Inventory fresh = testEntityManager.find(Inventory.class, id);
        fresh.adjustAvailableStock(5);
        testEntityManager.flush();
        testEntityManager.clear();

        // Re-saving the stale (version 0) snapshot must be rejected.
        stale.adjustAvailableStock(1);
        assertThatThrownBy(() -> inventoryRepository.saveAndFlush(stale))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
