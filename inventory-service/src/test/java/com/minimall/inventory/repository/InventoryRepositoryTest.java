package com.minimall.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class InventoryRepositoryTest {

    @Autowired
    private InventoryRepository inventoryRepository;

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
}
