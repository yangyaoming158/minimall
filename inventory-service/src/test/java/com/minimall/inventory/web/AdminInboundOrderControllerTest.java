package com.minimall.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.auth.context.AuthRole;
import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.core.audit.AdminAuditWriter;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.inventory.domain.InboundOrder;
import com.minimall.inventory.domain.InboundOrderItem;
import com.minimall.inventory.domain.InboundOrderStatus;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.dto.CreateInboundOrderDraftItemRequest;
import com.minimall.inventory.dto.CreateInboundOrderDraftRequest;
import com.minimall.inventory.repository.InboundOrderItemRepository;
import com.minimall.inventory.repository.InboundOrderRepository;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_inbound_order_controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "minimall.auth.jwt.secret=test-jwt-secret-for-admin-inbound-order-controller",
        "minimall.auth.jwt.expire-seconds=3600"
})
class AdminInboundOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private InboundOrderRepository inboundOrderRepository;

    @Autowired
    private InboundOrderItemRepository inboundOrderItemRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryRecordRepository inventoryRecordRepository;

    @MockBean
    private AdminAuditWriter adminAuditWriter;

    @BeforeEach
    void setUp() {
        inboundOrderItemRepository.deleteAll();
        inboundOrderRepository.deleteAll();
        inventoryRecordRepository.deleteAll();
        inventoryRepository.deleteAll();
        reset(adminAuditWriter);
    }

    @Test
    void createDraftRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/inbound-orders/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(draftRequest(item("SKU-INB-AUTH", 1)))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void createDraftRejectsNonAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/inbound-orders/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(draftRequest(item("SKU-INB-USER", 1)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void createMultiItemDraftDoesNotMutateInventory() throws Exception {
        inventoryRepository.saveAndFlush(buildInventory("SKU-INB-CREATE-1", 12, 2, 5));
        long beforeInventoryCount = inventoryRepository.count();
        long beforeRecordCount = inventoryRecordRepository.count();

        mockMvc.perform(post("/api/admin/inbound-orders/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(draftRequest(
                                item(" SKU-INB-CREATE-1 ", 5),
                                item("SKU-INB-CREATE-2", 8)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.inboundNo").exists())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.source").value("ADMIN_MANUAL"))
                .andExpect(jsonPath("$.data.createdByAdminUserId").value(42))
                .andExpect(jsonPath("$.data.createdByAdminUsername").value("admin"))
                .andExpect(jsonPath("$.data.itemCount").value(2))
                .andExpect(jsonPath("$.data.totalQuantity").value(13))
                .andExpect(jsonPath("$.data.items[0].productId").value("SKU-INB-CREATE-1"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(5))
                .andExpect(jsonPath("$.data.items[1].productId").value("SKU-INB-CREATE-2"))
                .andExpect(jsonPath("$.data.items[1].quantity").value(8));

        assertThat(inboundOrderRepository.count()).isEqualTo(1);
        assertThat(inboundOrderItemRepository.count()).isEqualTo(2);
        assertThat(inventoryRepository.count()).isEqualTo(beforeInventoryCount);
        assertThat(inventoryRecordRepository.count()).isEqualTo(beforeRecordCount);
        assertThat(inventoryRepository.findByProductId("SKU-INB-CREATE-1"))
                .get()
                .satisfies(inventory -> {
                    assertThat(inventory.getAvailableStock()).isEqualTo(12);
                    assertThat(inventory.getLockedStock()).isEqualTo(2);
                    assertThat(inventory.getSafetyStock()).isEqualTo(5);
                });
    }

    @Test
    void createDraftRejectsDuplicateProducts() throws Exception {
        mockMvc.perform(post("/api/admin/inbound-orders/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(draftRequest(
                                item("SKU-INB-DUP", 2),
                                item(" SKU-INB-DUP ", 3)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("Duplicate productId in inbound draft"));

        assertThat(inboundOrderRepository.count()).isZero();
        assertThat(inboundOrderItemRepository.count()).isZero();
    }

    @Test
    void listReturnsPagedDraftsAndFiltersByStatus() throws Exception {
        saveInboundOrder("INB-LIST-DRAFT", InboundOrderStatus.DRAFT, item("SKU-INB-LIST-1", 4));
        saveInboundOrder("INB-LIST-CANCELLED", InboundOrderStatus.CANCELLED, item("SKU-INB-LIST-2", 7));

        mockMvc.perform(get("/api/admin/inbound-orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].itemCount").value(1))
                .andExpect(jsonPath("$.data.content[0].items.length()").value(1));

        mockMvc.perform(get("/api/admin/inbound-orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("status", "cancelled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].inboundNo").value("INB-LIST-CANCELLED"))
                .andExpect(jsonPath("$.data.content[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.content[0].source").value("ADMIN_MANUAL"))
                .andExpect(jsonPath("$.data.content[0].totalQuantity").value(7));
    }

    @Test
    void listRejectsInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/admin/inbound-orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("status", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("Invalid inbound order status"));
    }

    @Test
    void detailReturnsItems() throws Exception {
        saveInboundOrder(
                "INB-DETAIL",
                InboundOrderStatus.DRAFT,
                item("SKU-INB-DETAIL-1", 3),
                item("SKU-INB-DETAIL-2", 6));

        mockMvc.perform(get("/api/admin/inbound-orders/INB-DETAIL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.inboundNo").value("INB-DETAIL"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.itemCount").value(2))
                .andExpect(jsonPath("$.data.totalQuantity").value(9))
                .andExpect(jsonPath("$.data.items[0].productId").value("SKU-INB-DETAIL-1"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(3))
                .andExpect(jsonPath("$.data.items[1].productId").value("SKU-INB-DETAIL-2"))
                .andExpect(jsonPath("$.data.items[1].quantity").value(6));
    }

    @Test
    void detailReturnsNotFoundForUnknownInboundNo() throws Exception {
        mockMvc.perform(get("/api/admin/inbound-orders/INB-MISSING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("Inbound order not found"));
    }

    @Test
    void cancelRejectsNonAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/inbound-orders/INB-CANCEL-USER/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void cancelDraftDoesNotMutateInventoryAndIsRepeatSafe() throws Exception {
        inventoryRepository.saveAndFlush(buildInventory("SKU-INB-CANCEL", 20, 1, 3));
        saveInboundOrder("INB-CANCEL", InboundOrderStatus.DRAFT, item("SKU-INB-CANCEL", 10));
        long beforeRecordCount = inventoryRecordRepository.count();

        mockMvc.perform(post("/api/admin/inbound-orders/INB-CANCEL/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.inboundNo").value("INB-CANCEL"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.items[0].productId").value("SKU-INB-CANCEL"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(10));

        mockMvc.perform(post("/api/admin/inbound-orders/INB-CANCEL/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertThat(inboundOrderRepository.findByInboundNo("INB-CANCEL"))
                .get()
                .extracting(InboundOrder::getStatus)
                .isEqualTo(InboundOrderStatus.CANCELLED);
        assertThat(inventoryRecordRepository.count()).isEqualTo(beforeRecordCount);
        assertThat(inventoryRepository.findByProductId("SKU-INB-CANCEL"))
                .get()
                .satisfies(inventory -> {
                    assertThat(inventory.getAvailableStock()).isEqualTo(20);
                    assertThat(inventory.getLockedStock()).isEqualTo(1);
                    assertThat(inventory.getSafetyStock()).isEqualTo(3);
                });
    }

    @Test
    void cancelRejectsNonDraftStatus() throws Exception {
        saveInboundOrder("INB-CANCEL-CONFIRMED", InboundOrderStatus.CONFIRMED, item("SKU-INB-CONF", 2));

        mockMvc.perform(post("/api/admin/inbound-orders/INB-CANCEL-CONFIRMED/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()))
                .andExpect(jsonPath("$.message").value("Only draft inbound orders can be cancelled"));
    }

    private void saveInboundOrder(
            String inboundNo,
            InboundOrderStatus status,
            CreateInboundOrderDraftItemRequest... items) {
        InboundOrder order = new InboundOrder(inboundNo, 42L, "admin");
        order.setStatus(status);
        inboundOrderRepository.saveAndFlush(order);
        inboundOrderItemRepository.saveAllAndFlush(List.of(items).stream()
                .map(item -> new InboundOrderItem(inboundNo, item.productId(), item.quantity()))
                .toList());
    }

    private Inventory buildInventory(String productId, int available, int locked, int safetyStock) {
        Inventory inventory = new Inventory(productId, available, locked);
        inventory.setSafetyStock(safetyStock);
        return inventory;
    }

    private CreateInboundOrderDraftRequest draftRequest(CreateInboundOrderDraftItemRequest... items) {
        return new CreateInboundOrderDraftRequest(List.of(items));
    }

    private CreateInboundOrderDraftItemRequest item(String productId, int quantity) {
        return new CreateInboundOrderDraftItemRequest(productId, quantity);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String adminToken() {
        return jwtUtils.generateToken(42L, "admin", AuthRole.ADMIN);
    }

    private String userToken() {
        return jwtUtils.generateToken(43L, "alice", AuthRole.USER);
    }
}
