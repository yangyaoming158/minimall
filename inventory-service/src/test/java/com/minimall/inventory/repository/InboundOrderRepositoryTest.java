package com.minimall.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minimall.inventory.domain.InboundOrder;
import com.minimall.inventory.domain.InboundOrderItem;
import com.minimall.inventory.domain.InboundOrderSource;
import com.minimall.inventory.domain.InboundOrderStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class InboundOrderRepositoryTest {

    @Autowired
    private InboundOrderRepository inboundOrderRepository;

    @Autowired
    private InboundOrderItemRepository inboundOrderItemRepository;

    @Test
    void savesDraftAndFindsByInboundNo() {
        InboundOrder saved = inboundOrderRepository.saveAndFlush(
                new InboundOrder(" INB-20260603-001 ", 1001L, " ops-admin "));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(inboundOrderRepository.findByInboundNo("INB-20260603-001"))
                .isPresent()
                .get()
                .satisfies(order -> {
                    assertThat(order.getInboundNo()).isEqualTo("INB-20260603-001");
                    assertThat(order.getStatus()).isEqualTo(InboundOrderStatus.DRAFT);
                    assertThat(order.getSource()).isEqualTo(InboundOrderSource.ADMIN_MANUAL);
                    assertThat(order.getCreatedByAdminUserId()).isEqualTo(1001L);
                    assertThat(order.getCreatedByAdminUsername()).isEqualTo("ops-admin");
                });
        assertThat(inboundOrderRepository.existsByInboundNo("INB-20260603-001")).isTrue();
    }

    @Test
    void persistsStatusAndSourceAsEnumValues() {
        InboundOrder order = new InboundOrder(
                "INB-AI-001",
                InboundOrderSource.AI_SUGGESTION,
                1002L,
                "ai-reviewer");
        order.setStatus(InboundOrderStatus.CONFIRMED);
        inboundOrderRepository.saveAndFlush(order);

        assertThat(inboundOrderRepository.findByInboundNo("INB-AI-001"))
                .isPresent()
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getStatus()).isEqualTo(InboundOrderStatus.CONFIRMED);
                    assertThat(saved.getSource()).isEqualTo(InboundOrderSource.AI_SUGGESTION);
                });
        Page<InboundOrder> confirmedOrders =
                inboundOrderRepository.findByStatus(InboundOrderStatus.CONFIRMED, PageRequest.of(0, 10));
        assertThat(confirmedOrders.getContent())
                .extracting(InboundOrder::getInboundNo)
                .containsExactly("INB-AI-001");
    }

    @Test
    void persistsConfirmationStateAndFindsByRequestId() {
        InboundOrder order = new InboundOrder("INB-CONFIRM-REQ", 1001L, "ops-admin");
        order.confirm(" REQ-CONFIRM-1 ", 1002L, " reviewer ");
        inboundOrderRepository.saveAndFlush(order);

        assertThat(inboundOrderRepository.findByConfirmRequestId("REQ-CONFIRM-1"))
                .isPresent()
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getInboundNo()).isEqualTo("INB-CONFIRM-REQ");
                    assertThat(saved.getStatus()).isEqualTo(InboundOrderStatus.CONFIRMED);
                    assertThat(saved.getConfirmRequestId()).isEqualTo("REQ-CONFIRM-1");
                    assertThat(saved.getConfirmedByAdminUserId()).isEqualTo(1002L);
                    assertThat(saved.getConfirmedByAdminUsername()).isEqualTo("reviewer");
                    assertThat(saved.getConfirmedAt()).isNotNull();
                });
    }

    @Test
    void duplicateConfirmRequestIdViolatesUniqueConstraint() {
        InboundOrder first = new InboundOrder("INB-CONFIRM-DUP-1", 1001L, "ops-admin");
        first.confirm("REQ-CONFIRM-DUP", 1002L, "reviewer");
        inboundOrderRepository.saveAndFlush(first);

        InboundOrder second = new InboundOrder("INB-CONFIRM-DUP-2", 1001L, "ops-admin");
        second.confirm("REQ-CONFIRM-DUP", 1003L, "other-reviewer");

        assertThatThrownBy(() -> inboundOrderRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateInboundNoViolatesUniqueConstraint() {
        inboundOrderRepository.saveAndFlush(new InboundOrder("INB-DUP", 1001L, "ops-admin"));

        assertThatThrownBy(() -> inboundOrderRepository.saveAndFlush(
                new InboundOrder("INB-DUP", 1002L, "other-admin")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savesItemsAndFindsByInboundNo() {
        inboundOrderRepository.saveAndFlush(new InboundOrder("INB-ITEM-001", 1001L, "ops-admin"));
        inboundOrderItemRepository.saveAll(List.of(
                new InboundOrderItem("INB-ITEM-001", "SKU-INB-1", 5),
                new InboundOrderItem("INB-ITEM-001", "SKU-INB-2", 8)));
        inboundOrderItemRepository.flush();

        List<InboundOrderItem> items =
                inboundOrderItemRepository.findByInboundNoOrderByIdAsc("INB-ITEM-001");

        assertThat(items)
                .extracting(InboundOrderItem::getProductId)
                .containsExactly("SKU-INB-1", "SKU-INB-2");
        assertThat(items)
                .extracting(InboundOrderItem::getQuantity)
                .containsExactly(5, 8);
        assertThat(items)
                .allSatisfy(item -> {
                    assertThat(item.getId()).isNotNull();
                    assertThat(item.getCreatedAt()).isNotNull();
                    assertThat(item.getUpdatedAt()).isNotNull();
                });
        assertThat(inboundOrderItemRepository.findByInboundNoIn(List.of("INB-ITEM-001"))).hasSize(2);
    }

    @Test
    void duplicateProductInOneInboundOrderViolatesUniqueConstraint() {
        inboundOrderRepository.saveAndFlush(new InboundOrder("INB-ITEM-DUP", 1001L, "ops-admin"));
        inboundOrderItemRepository.saveAndFlush(
                new InboundOrderItem("INB-ITEM-DUP", "SKU-DUP", 5));

        assertThatThrownBy(() -> inboundOrderItemRepository.saveAndFlush(
                new InboundOrderItem("INB-ITEM-DUP", "SKU-DUP", 6)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNonPositiveItemQuantity() {
        assertThatThrownBy(() -> new InboundOrderItem("INB-INVALID", "SKU-INVALID", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("quantity must be positive");
    }

    @Test
    void enumsExposeStableContractValues() {
        assertThat(InboundOrderStatus.values())
                .containsExactly(
                        InboundOrderStatus.DRAFT,
                        InboundOrderStatus.CONFIRMED,
                        InboundOrderStatus.APPLIED,
                        InboundOrderStatus.CANCELLED);
        assertThat(InboundOrderSource.values())
                .containsExactly(
                        InboundOrderSource.ADMIN_MANUAL,
                        InboundOrderSource.AI_SUGGESTION);
    }
}
