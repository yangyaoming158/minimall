package com.minimall.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryRecordSourceType;
import com.minimall.inventory.domain.InventoryRecordStatus;
import com.minimall.inventory.dto.InventoryRecordResponse;
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
                    assertThat(record.getOrderNo()).isEqualTo("ORDER-1001");
                    assertThat(record.getRequestId()).isEqualTo("ORDER-1001");
                    assertThat(record.getSourceType()).isEqualTo(InventoryRecordSourceType.ORDER_DEDUCT);
                    assertThat(record.getReferenceNo()).isEqualTo("ORDER-1001");
                    assertThat(record.getQuantity()).isEqualTo(3);
                    assertThat(record.getReason()).isNull();
                    assertThat(record.getAdminUserId()).isNull();
                    assertThat(record.getAdminUsername()).isNull();
                    assertThat(record.getStatus()).isEqualTo(InventoryRecordStatus.SUCCESS);
                });
        assertThat(inventoryRecordRepository.existsByOrderNoAndChangeType("ORDER-1001", InventoryChangeType.DEDUCT))
                .isTrue();
        assertThat(inventoryRecordRepository.existsBySourceTypeAndRequestId(
                InventoryRecordSourceType.ORDER_DEDUCT, "ORDER-1001"))
                .isTrue();
        assertThat(inventoryRecordRepository.findBySourceTypeAndRequestId(
                InventoryRecordSourceType.ORDER_DEDUCT, "ORDER-1001"))
                .isPresent();
        assertThat(inventoryRecordRepository.findByProductId("SKU-INV-2001")).hasSize(1);
    }

    @Test
    void sameOrderNoAndDifferentChangeTypeAreAllowed() {
        inventoryRecordRepository.saveAndFlush(
                new InventoryRecord("SKU-INV-2002", "ORDER-1002", InventoryChangeType.DEDUCT, 2));
        inventoryRecordRepository.saveAndFlush(
                new InventoryRecord("SKU-INV-2002", "ORDER-1002", InventoryChangeType.RELEASE, 2));

        assertThat(inventoryRecordRepository.findByProductId("SKU-INV-2002")).hasSize(2);
        assertThat(inventoryRecordRepository.findBySourceTypeAndRequestId(
                InventoryRecordSourceType.ORDER_DEDUCT, "ORDER-1002"))
                .isPresent();
        assertThat(inventoryRecordRepository.findBySourceTypeAndRequestId(
                InventoryRecordSourceType.ORDER_RELEASE, "ORDER-1002"))
                .isPresent();
    }

    @Test
    void duplicateOrderNoAndChangeTypeViolatesUniqueConstraint() {
        inventoryRecordRepository.saveAndFlush(
                new InventoryRecord("SKU-INV-2003", "ORDER-DUP", InventoryChangeType.DEDUCT, 1));

        assertThatThrownBy(() -> inventoryRecordRepository.saveAndFlush(
                new InventoryRecord("SKU-INV-2003", "ORDER-DUP", InventoryChangeType.DEDUCT, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateSourceTypeRequestIdAndProductIdViolatesUniqueConstraint() {
        inventoryRecordRepository.saveAndFlush(new InventoryRecord(
                "SKU-INV-2004",
                null,
                InventoryChangeType.DEDUCT,
                1,
                "REQ-DUP",
                "cycle count",
                99L,
                "admin",
                InventoryRecordSourceType.ADMIN_ADJUSTMENT,
                "ADJ-1"));

        inventoryRecordRepository.saveAndFlush(new InventoryRecord(
                "SKU-INV-2004-OTHER",
                null,
                InventoryChangeType.RELEASE,
                1,
                "REQ-DUP",
                "correction",
                99L,
                "admin",
                InventoryRecordSourceType.ADMIN_ADJUSTMENT,
                "ADJ-2"));

        assertThat(inventoryRecordRepository.findByProductId("SKU-INV-2004")).hasSize(1);
        assertThat(inventoryRecordRepository.findByProductId("SKU-INV-2004-OTHER")).hasSize(1);

        assertThatThrownBy(() -> inventoryRecordRepository.saveAndFlush(new InventoryRecord(
                "SKU-INV-2004",
                null,
                InventoryChangeType.RELEASE,
                1,
                "REQ-DUP",
                "same product duplicate",
                99L,
                "admin",
                InventoryRecordSourceType.ADMIN_ADJUSTMENT,
                "ADJ-3")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void traceabilityFieldsAreNormalizedAndReturnedInResponse() {
        InventoryRecord saved = inventoryRecordRepository.saveAndFlush(new InventoryRecord(
                "SKU-INV-2005",
                null,
                InventoryChangeType.DEDUCT,
                5,
                " REQ-TRACE ",
                " restock correction ",
                100L,
                " admin-user ",
                InventoryRecordSourceType.ADMIN_INITIALIZE,
                " INIT-1001 "));

        assertThat(saved.getOrderNo()).isNull();
        assertThat(saved.getRequestId()).isEqualTo("REQ-TRACE");
        assertThat(saved.getReason()).isEqualTo("restock correction");
        assertThat(saved.getAdminUserId()).isEqualTo(100L);
        assertThat(saved.getAdminUsername()).isEqualTo("admin-user");
        assertThat(saved.getSourceType()).isEqualTo(InventoryRecordSourceType.ADMIN_INITIALIZE);
        assertThat(saved.getReferenceNo()).isEqualTo("INIT-1001");

        InventoryRecordResponse response = InventoryRecordResponse.from(saved);

        assertThat(response.id()).isEqualTo(saved.getId());
        assertThat(response.productId()).isEqualTo("SKU-INV-2005");
        assertThat(response.orderNo()).isNull();
        assertThat(response.requestId()).isEqualTo("REQ-TRACE");
        assertThat(response.changeType()).isEqualTo(InventoryChangeType.DEDUCT);
        assertThat(response.sourceType()).isEqualTo(InventoryRecordSourceType.ADMIN_INITIALIZE);
        assertThat(response.quantity()).isEqualTo(5);
        assertThat(response.reason()).isEqualTo("restock correction");
        assertThat(response.adminUserId()).isEqualTo(100L);
        assertThat(response.adminUsername()).isEqualTo("admin-user");
        assertThat(response.referenceNo()).isEqualTo("INIT-1001");
        assertThat(response.status()).isEqualTo(InventoryRecordStatus.SUCCESS);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void sourceTypesIncludeInboundOrderAndReservedAiSuggestionValues() {
        assertThat(InventoryRecordSourceType.values())
                .contains(
                        InventoryRecordSourceType.ORDER_DEDUCT,
                        InventoryRecordSourceType.ORDER_RELEASE,
                        InventoryRecordSourceType.ADMIN_INITIALIZE,
                        InventoryRecordSourceType.ADMIN_ADJUSTMENT,
                        InventoryRecordSourceType.INBOUND_ORDER,
                        InventoryRecordSourceType.AI_SUGGESTION);
    }

    @Test
    void inboundOrderSourcePersistsAndSerializesTraceabilityFields() {
        InventoryRecord saved = inventoryRecordRepository.saveAndFlush(new InventoryRecord(
                "SKU-INBOUND-1",
                null,
                InventoryChangeType.ADJUST_INCREASE,
                12,
                "REQ-INBOUND-1",
                "Confirmed inbound order",
                101L,
                "ops-admin",
                InventoryRecordSourceType.INBOUND_ORDER,
                "INB-20260601-001"));

        assertThat(saved.getSourceType()).isEqualTo(InventoryRecordSourceType.INBOUND_ORDER);
        assertThat(saved.getReferenceNo()).isEqualTo("INB-20260601-001");
        assertThat(saved.getAdminUserId()).isEqualTo(101L);
        assertThat(saved.getAdminUsername()).isEqualTo("ops-admin");

        InventoryRecordResponse response = InventoryRecordResponse.from(saved);

        assertThat(response.sourceType()).isEqualTo(InventoryRecordSourceType.INBOUND_ORDER);
        assertThat(response.requestId()).isEqualTo("REQ-INBOUND-1");
        assertThat(response.referenceNo()).isEqualTo("INB-20260601-001");
        assertThat(response.adminUserId()).isEqualTo(101L);
        assertThat(response.adminUsername()).isEqualTo("ops-admin");
        assertThat(response.status()).isEqualTo(InventoryRecordStatus.SUCCESS);
    }
}
