package com.minimall.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryRecordStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class InventoryRecordRepositoryTest {

    @Autowired
    private InventoryRecordRepository inventoryRecordRepository;

    @Test
    void savesRecordAndFindsByOrderNoAndChangeType() {
        InventoryRecord saved = inventoryRecordRepository.saveAndFlush(
                new InventoryRecord("SKU-INV-2001", "ORDER-1001", InventoryChangeType.DEDUCT, 3));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(inventoryRecordRepository.findByOrderNoAndChangeType("ORDER-1001", InventoryChangeType.DEDUCT))
                .isPresent()
                .get()
                .satisfies(record -> {
                    assertThat(record.getProductId()).isEqualTo("SKU-INV-2001");
                    assertThat(record.getQuantity()).isEqualTo(3);
                    assertThat(record.getStatus()).isEqualTo(InventoryRecordStatus.SUCCESS);
                });
        assertThat(inventoryRecordRepository.existsByOrderNoAndChangeType("ORDER-1001", InventoryChangeType.DEDUCT))
                .isTrue();
        assertThat(inventoryRecordRepository.findByProductId("SKU-INV-2001")).hasSize(1);
    }

    @Test
    void sameOrderNoAndDifferentChangeTypeAreAllowed() {
        inventoryRecordRepository.saveAndFlush(
                new InventoryRecord("SKU-INV-2002", "ORDER-1002", InventoryChangeType.DEDUCT, 2));
        inventoryRecordRepository.saveAndFlush(
                new InventoryRecord("SKU-INV-2002", "ORDER-1002", InventoryChangeType.RELEASE, 2));

        assertThat(inventoryRecordRepository.findByProductId("SKU-INV-2002")).hasSize(2);
    }

    @Test
    void duplicateOrderNoAndChangeTypeViolatesUniqueConstraint() {
        inventoryRecordRepository.saveAndFlush(
                new InventoryRecord("SKU-INV-2003", "ORDER-DUP", InventoryChangeType.DEDUCT, 1));

        assertThatThrownBy(() -> inventoryRecordRepository.saveAndFlush(
                new InventoryRecord("SKU-INV-2003", "ORDER-DUP", InventoryChangeType.DEDUCT, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
