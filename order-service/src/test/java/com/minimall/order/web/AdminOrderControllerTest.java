package com.minimall.order.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minimall.common.auth.constants.AuthHeaders;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.order.client.inventory.InventoryClient;
import com.minimall.order.client.product.ProductValidationService;
import com.minimall.order.domain.Order;
import com.minimall.order.domain.OrderStateMachine;
import com.minimall.order.domain.OrderStatus;
import com.minimall.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_order_controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "minimall.order.payment-expire-seconds=900",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class AdminOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ProductValidationService productValidationService;

    @MockBean
    private InventoryClient inventoryClient;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private final ValueOperations<String, String> redisValueOperations =
            org.mockito.Mockito.mock(ValueOperations.class);

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        reset(productValidationService, inventoryClient, redisTemplate, redisValueOperations);
        given(redisTemplate.opsForValue()).willReturn(redisValueOperations);
    }

    @Test
    void productSalesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/orders/product-sales"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    void productSalesRejectsUserRole() throws Exception {
        mockMvc.perform(get("/api/admin/orders/product-sales")
                        .header(AuthHeaders.USER_ID, "501")
                        .header(AuthHeaders.USERNAME, "operator")
                        .header(AuthHeaders.USER_ROLE, "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void productSalesReturnsPagedAggregationForAdmin() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 20, 10, 0);
        saveOrder(order(
                "ORD-ADMIN-SALES-1001",
                601L,
                "SKU-ADMIN-A",
                2,
                new BigDecimal("19.90"),
                OrderStatus.PAID),
                createdAt.plusHours(1));
        saveOrder(order(
                "ORD-ADMIN-SALES-1002",
                602L,
                "SKU-ADMIN-A",
                3,
                new BigDecimal("19.90"),
                OrderStatus.PAID),
                createdAt.plusHours(2));
        saveOrder(order(
                "ORD-ADMIN-SALES-1003",
                603L,
                "SKU-ADMIN-B",
                1,
                new BigDecimal("8.50"),
                OrderStatus.PAID),
                createdAt.plusHours(3));
        saveOrder(order(
                "ORD-ADMIN-SALES-1004",
                604L,
                "SKU-ADMIN-A",
                7,
                new BigDecimal("19.90"),
                OrderStatus.PENDING_PAYMENT),
                createdAt.plusHours(4));

        mockMvc.perform(get("/api/admin/orders/product-sales")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("status", "PAID")
                        .param("createdFrom", "2026-05-20T00:00:00")
                        .param("createdTo", "2026-05-21T00:00:00")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.content[0].productId").value("SKU-ADMIN-A"))
                .andExpect(jsonPath("$.data.content[0].quantitySold").value(5))
                .andExpect(jsonPath("$.data.content[0].orderCount").value(2))
                .andExpect(jsonPath("$.data.content[0].totalAmount").value(99.50))
                .andExpect(jsonPath("$.data.content[0].id").doesNotExist())
                .andExpect(jsonPath("$.data.content[1].productId").value("SKU-ADMIN-B"))
                .andExpect(jsonPath("$.data.content[1].quantitySold").value(1))
                .andExpect(jsonPath("$.data.content[1].orderCount").value(1))
                .andExpect(jsonPath("$.data.content[1].totalAmount").value(8.50));
    }

    @Test
    void productSalesSupportsProductFilterAndBoundsPageSize() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 21, 10, 0);
        saveOrder(order(
                "ORD-ADMIN-SALES-1005",
                605L,
                "SKU-ADMIN-C",
                4,
                new BigDecimal("15.00"),
                OrderStatus.PAID),
                createdAt);
        saveOrder(order(
                "ORD-ADMIN-SALES-1006",
                606L,
                "SKU-ADMIN-D",
                2,
                new BigDecimal("30.00"),
                OrderStatus.PAID),
                createdAt);

        mockMvc.perform(get("/api/admin/orders/product-sales")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("productId", " SKU-ADMIN-C ")
                        .param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productId").value("SKU-ADMIN-C"))
                .andExpect(jsonPath("$.data.content[0].quantitySold").value(4))
                .andExpect(jsonPath("$.data.content[0].orderCount").value(1))
                .andExpect(jsonPath("$.data.content[0].totalAmount").value(60.00));
    }

    @Test
    void productSalesRejectsInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/admin/orders/product-sales")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("Invalid status"));
    }

    @Test
    void productSalesRejectsInvalidDateRange() throws Exception {
        mockMvc.perform(get("/api/admin/orders/product-sales")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("createdFrom", "2026-05-22T00:00:00")
                        .param("createdTo", "2026-05-21T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("createdFrom must be before or equal to createdTo"));
    }

    @Test
    void customerMyOrdersStillUsesCustomerContract() throws Exception {
        saveOrder(order(
                "ORD-CUSTOMER-UNCHANGED-1001",
                701L,
                "SKU-CUSTOMER-A",
                2,
                new BigDecimal("19.90"),
                OrderStatus.PENDING_PAYMENT),
                LocalDateTime.of(2026, 5, 22, 10, 0));

        mockMvc.perform(get("/api/orders/my")
                        .header(AuthHeaders.USER_ID, "701")
                        .header(AuthHeaders.USERNAME, "customer")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].orderNo").value("ORD-CUSTOMER-UNCHANGED-1001"))
                .andExpect(jsonPath("$.data.content[0].items[0].productId").value("SKU-CUSTOMER-A"))
                .andExpect(jsonPath("$.data.content[0].quantitySold").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].orderCount").doesNotExist());
    }

    private Order order(
            String orderNo,
            Long userId,
            String productId,
            int quantity,
            BigDecimal unitPrice,
            OrderStatus status) {
        Order order = new Order(
                orderNo,
                userId,
                "admin-sales-user",
                productId,
                "Admin Sales Product",
                quantity,
                unitPrice,
                unitPrice.multiply(BigDecimal.valueOf(quantity)),
                "idem-" + orderNo,
                LocalDateTime.of(2026, 5, 30, 12, 0));
        if (status != OrderStatus.PENDING_PAYMENT) {
            new OrderStateMachine().transition(order, status, LocalDateTime.of(2026, 5, 20, 9, 0));
        }
        return order;
    }

    private void saveOrder(Order order, LocalDateTime createdAt) {
        orderRepository.saveAndFlush(order);
        jdbcTemplate.update(
                "update orders set created_at = ?, updated_at = ? where order_no = ?",
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt),
                order.getOrderNo());
        assertThat(orderRepository.findByOrderNo(order.getOrderNo())).isPresent();
    }
}
