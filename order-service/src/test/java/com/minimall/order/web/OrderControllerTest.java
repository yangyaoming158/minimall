package com.minimall.order.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minimall.common.auth.constants.AuthHeaders;
import com.minimall.common.auth.context.UserContextHolder;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.order.domain.Order;
import com.minimall.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:order_controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        UserContextHolder.clear();
    }

    @Test
    void detailReturnsStableDtoWithoutInternalFields() throws Exception {
        orderRepository.saveAndFlush(order("ORD-API-1001", 101L, "idem-api-1001"));

        mockMvc.perform(get("/api/orders/ORD-API-1001")
                        .header(AuthHeaders.USER_ID, "101")
                        .header(AuthHeaders.USERNAME, "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.username").doesNotExist())
                .andExpect(jsonPath("$.data.idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.data.orderNo").value("ORD-API-1001"))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.totalAmount").value(39.80))
                .andExpect(jsonPath("$.data.items[0].productId").value("SKU-API-1001"))
                .andExpect(jsonPath("$.data.items[0].productName").value("API Product"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(19.90))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists())
                .andExpect(jsonPath("$.data.expireAt").exists());
    }

    @Test
    void myOrdersReturnsPagedStableDto() throws Exception {
        orderRepository.saveAndFlush(order("ORD-API-1002", 102L, "idem-api-1002"));

        mockMvc.perform(get("/api/orders/my")
                        .header(AuthHeaders.USER_ID, "102")
                        .header(AuthHeaders.USERNAME, "bob")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.content[0].id").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].username").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].orderNo").value("ORD-API-1002"))
                .andExpect(jsonPath("$.data.content[0].items[0].productId").value("SKU-API-1001"));
    }

    @Test
    void myOrdersReturnsEmptyPageAsSuccess() throws Exception {
        mockMvc.perform(get("/api/orders/my")
                        .header(AuthHeaders.USER_ID, "103")
                        .header(AuthHeaders.USERNAME, "carol")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0));
    }

    @Test
    void missingAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/orders/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    void missingOrderReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/orders/MISSING")
                        .header(AuthHeaders.USER_ID, "104")
                        .header(AuthHeaders.USERNAME, "dave"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("Order not found"));
    }

    @Test
    void orderOwnedByAnotherUserReturnsNotFound() throws Exception {
        orderRepository.saveAndFlush(order("ORD-API-1003", 105L, "idem-api-1003"));

        mockMvc.perform(get("/api/orders/ORD-API-1003")
                        .header(AuthHeaders.USER_ID, "999")
                        .header(AuthHeaders.USERNAME, "mallory"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("Order not found"));
    }

    private Order order(String orderNo, Long userId, String idempotencyKey) {
        return new Order(
                orderNo,
                userId,
                "alice",
                "SKU-API-1001",
                "API Product",
                2,
                new BigDecimal("19.90"),
                new BigDecimal("39.80"),
                idempotencyKey,
                LocalDateTime.of(2026, 5, 7, 21, 0));
    }
}
