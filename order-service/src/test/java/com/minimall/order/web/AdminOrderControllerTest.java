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
import com.minimall.order.domain.OrderEvent;
import com.minimall.order.domain.OrderEventType;
import com.minimall.order.domain.OrderStateMachine;
import com.minimall.order.domain.OrderStatus;
import com.minimall.order.repository.OrderEventRepository;
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
import java.util.EnumSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

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
    private OrderEventRepository orderEventRepository;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

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
        orderEventRepository.deleteAll();
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
    void salesByProductStatsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/operation-stats/sales-by-product"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void salesByProductStatsRejectsUserRole() throws Exception {
        mockMvc.perform(get("/api/admin/operation-stats/sales-by-product")
                        .header(AuthHeaders.USER_ID, "501")
                        .header(AuthHeaders.USERNAME, "operator")
                        .header(AuthHeaders.USER_ROLE, "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void salesByProductStatsReturnsReadOnlyAggregationForAdmin() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 24, 10, 0);
        saveOrder(order(
                "ORD-OP-STATS-1001",
                611L,
                "SKU-OP-A",
                2,
                new BigDecimal("19.90"),
                OrderStatus.PAID),
                createdAt.plusHours(1));
        saveOrder(order(
                "ORD-OP-STATS-1002",
                612L,
                "SKU-OP-A",
                3,
                new BigDecimal("19.90"),
                OrderStatus.PAID),
                createdAt.plusHours(2));
        saveOrder(order(
                "ORD-OP-STATS-1003",
                613L,
                "SKU-OP-B",
                4,
                new BigDecimal("8.50"),
                OrderStatus.PAID),
                createdAt.plusHours(3));
        saveOrder(order(
                "ORD-OP-STATS-PENDING",
                615L,
                "SKU-OP-PENDING",
                99,
                new BigDecimal("999.00"),
                OrderStatus.PENDING_PAYMENT),
                createdAt.plusHours(4));
        saveOrder(order(
                "ORD-OP-STATS-OLD",
                614L,
                "SKU-OP-C",
                10,
                new BigDecimal("99.00"),
                OrderStatus.PAID),
                createdAt.minusDays(5));

        long beforeCount = orderRepository.count();

        mockMvc.perform(get("/api/admin/operation-stats/sales-by-product")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("createdFrom", "2026-05-24T00:00:00")
                        .param("createdTo", "2026-05-25T00:00:00")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.content[0].productId").value("SKU-OP-A"))
                .andExpect(jsonPath("$.data.content[0].orderCount").value(2))
                .andExpect(jsonPath("$.data.content[0].soldQuantity").value(5))
                .andExpect(jsonPath("$.data.content[0].totalAmount").value(99.50))
                .andExpect(jsonPath("$.data.content[0].quantitySold").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].id").doesNotExist())
                .andExpect(jsonPath("$.data.content[1].productId").value("SKU-OP-B"))
                .andExpect(jsonPath("$.data.content[1].orderCount").value(1))
                .andExpect(jsonPath("$.data.content[1].soldQuantity").value(4))
                .andExpect(jsonPath("$.data.content[1].totalAmount").value(34.00));

        assertThat(orderRepository.count()).isEqualTo(beforeCount);
        assertThat(orderRepository.findByOrderNo("ORD-OP-STATS-1001"))
                .get()
                .extracting(Order::getStatus)
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void salesByProductStatsSupportsProductFilter() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 26, 10, 0);
        saveOrder(order(
                "ORD-OP-STATS-FILTER-1001",
                621L,
                "SKU-OP-FILTER-A",
                2,
                new BigDecimal("20.00"),
                OrderStatus.PAID),
                createdAt);
        saveOrder(order(
                "ORD-OP-STATS-FILTER-1002",
                622L,
                "SKU-OP-FILTER-B",
                3,
                new BigDecimal("15.00"),
                OrderStatus.PAID),
                createdAt.plusHours(1));

        mockMvc.perform(get("/api/admin/operation-stats/sales-by-product")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("productId", " SKU-OP-FILTER-A ")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productId").value("SKU-OP-FILTER-A"))
                .andExpect(jsonPath("$.data.content[0].soldQuantity").value(2))
                .andExpect(jsonPath("$.data.content[0].orderCount").value(1))
                .andExpect(jsonPath("$.data.content[0].totalAmount").value(40.00));
    }

    @Test
    void salesByProductStatsRejectsInvalidDateRange() throws Exception {
        mockMvc.perform(get("/api/admin/operation-stats/sales-by-product")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("createdFrom", "2026-05-25T00:00:00")
                        .param("createdTo", "2026-05-24T00:00:00"))
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

    @Test
    void adminOrderListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void adminOrderListRejectsUserRole() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .header(AuthHeaders.USER_ID, "501")
                        .header(AuthHeaders.USERNAME, "operator")
                        .header(AuthHeaders.USER_ROLE, "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void adminOrderListReturnsNewestFirstWithUsernameForAdmin() throws Exception {
        saveOrder(namedOrder("ORD-ADMIN-LIST-1001", 801L, "alice", "SKU-LIST-A", OrderStatus.PAID),
                LocalDateTime.of(2026, 5, 20, 10, 0));
        saveOrder(namedOrder("ORD-ADMIN-LIST-1002", 802L, "bob", "SKU-LIST-B", OrderStatus.PENDING_PAYMENT),
                LocalDateTime.of(2026, 5, 22, 10, 0));
        saveOrder(namedOrder("ORD-ADMIN-LIST-1003", 803L, "carol", "SKU-LIST-C", OrderStatus.CANCELLED),
                LocalDateTime.of(2026, 5, 21, 10, 0));

        mockMvc.perform(get("/api/admin/orders")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].orderNo").value("ORD-ADMIN-LIST-1002"))
                .andExpect(jsonPath("$.data.content[0].username").value("bob"))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.content[0].items[0].productId").value("SKU-LIST-B"))
                .andExpect(jsonPath("$.data.content[1].orderNo").value("ORD-ADMIN-LIST-1003"))
                .andExpect(jsonPath("$.data.content[2].orderNo").value("ORD-ADMIN-LIST-1001"));
    }

    @Test
    void adminOrderListFiltersByStatusUserIdAndProductId() throws Exception {
        saveOrder(namedOrder("ORD-ADMIN-FILT-1001", 811L, "dave", "SKU-FILT-A", OrderStatus.PAID),
                LocalDateTime.of(2026, 5, 20, 10, 0));
        saveOrder(namedOrder("ORD-ADMIN-FILT-1002", 811L, "dave", "SKU-FILT-B", OrderStatus.PENDING_PAYMENT),
                LocalDateTime.of(2026, 5, 20, 11, 0));
        saveOrder(namedOrder("ORD-ADMIN-FILT-1003", 812L, "erin", "SKU-FILT-A", OrderStatus.PAID),
                LocalDateTime.of(2026, 5, 20, 12, 0));

        mockMvc.perform(get("/api/admin/orders")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("status", "PAID")
                        .param("userId", "811")
                        .param("productId", " SKU-FILT-A "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].orderNo").value("ORD-ADMIN-FILT-1001"))
                .andExpect(jsonPath("$.data.content[0].userId").value(811));
    }

    @Test
    void adminOrderListFiltersByOrderNoUsernameAndDateRange() throws Exception {
        saveOrder(namedOrder("ORD-ADMIN-RANGE-1001", 821L, "fred", "SKU-RANGE-A", OrderStatus.PAID),
                LocalDateTime.of(2026, 5, 18, 10, 0));
        saveOrder(namedOrder("ORD-ADMIN-RANGE-1002", 822L, "gina", "SKU-RANGE-B", OrderStatus.PAID),
                LocalDateTime.of(2026, 5, 25, 10, 0));

        mockMvc.perform(get("/api/admin/orders")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("username", "gina")
                        .param("createdFrom", "2026-05-24T00:00:00")
                        .param("createdTo", "2026-05-26T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].orderNo").value("ORD-ADMIN-RANGE-1002"))
                .andExpect(jsonPath("$.data.content[0].username").value("gina"));

        mockMvc.perform(get("/api/admin/orders")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("orderNo", "ORD-ADMIN-RANGE-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].orderNo").value("ORD-ADMIN-RANGE-1001"));
    }

    @Test
    void adminOrderListRejectsInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
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
    void adminOrderListRejectsInvalidDateRange() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("createdFrom", "2026-05-22T00:00:00")
                        .param("createdTo", "2026-05-21T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("createdFrom must be before or equal to createdTo"));
    }

    @Test
    void adminOrderListBoundsPageSize() throws Exception {
        saveOrder(namedOrder("ORD-ADMIN-BOUND-1001", 831L, "hank", "SKU-BOUND-A", OrderStatus.PAID),
                LocalDateTime.of(2026, 5, 20, 10, 0));

        mockMvc.perform(get("/api/admin/orders")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void adminOrderDetailReturnsOrderForAdmin() throws Exception {
        saveOrder(namedOrder("ORD-ADMIN-DET-1001", 841L, "ivy", "SKU-DET-A", OrderStatus.PAID),
                LocalDateTime.of(2026, 5, 20, 10, 0));

        mockMvc.perform(get("/api/admin/orders/ORD-ADMIN-DET-1001")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNo").value("ORD-ADMIN-DET-1001"))
                .andExpect(jsonPath("$.data.userId").value(841))
                .andExpect(jsonPath("$.data.username").value("ivy"))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.items[0].productId").value("SKU-DET-A"))
                .andExpect(jsonPath("$.data.paidAt").exists());
    }

    @Test
    void adminOrderDetailReturnsNotFoundForUnknownOrder() throws Exception {
        mockMvc.perform(get("/api/admin/orders/ORD-DOES-NOT-EXIST")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("Order not found"));
    }

    @Test
    void adminOrderDetailRejectsUserRole() throws Exception {
        saveOrder(namedOrder("ORD-ADMIN-DET-FORBID", 842L, "jane", "SKU-DET-B", OrderStatus.PAID),
                LocalDateTime.of(2026, 5, 20, 10, 0));

        mockMvc.perform(get("/api/admin/orders/ORD-ADMIN-DET-FORBID")
                        .header(AuthHeaders.USER_ID, "842")
                        .header(AuthHeaders.USERNAME, "jane")
                        .header(AuthHeaders.USER_ROLE, "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void adminOrderEventsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/orders/ORD-ANY/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void adminOrderEventsRejectsUserRole() throws Exception {
        mockMvc.perform(get("/api/admin/orders/ORD-ANY/events")
                        .header(AuthHeaders.USER_ID, "501")
                        .header(AuthHeaders.USERNAME, "operator")
                        .header(AuthHeaders.USER_ROLE, "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void adminOrderEventsReturnsNotFoundForUnknownOrder() throws Exception {
        mockMvc.perform(get("/api/admin/orders/ORD-NO-SUCH/events")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("Order not found"));
    }

    @Test
    void adminOrderEventsUsesRecordedEventsForPaidOrder() throws Exception {
        saveOrder(namedOrder("ORD-EVT-PAID-1001", 851L, "kate", "SKU-EVT-A", OrderStatus.PAID),
                LocalDateTime.of(2026, 5, 20, 8, 0));
        saveEvent("pay-evt-1001", "ORD-EVT-PAID-1001", OrderStatus.PENDING_PAYMENT, OrderStatus.PAID,
                "{\"handleResult\":\"processed\"}", LocalDateTime.of(2026, 5, 20, 9, 0));

        mockMvc.perform(get("/api/admin/orders/ORD-EVT-PAID-1001/events")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].toStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data[0].eventType").doesNotExist())
                .andExpect(jsonPath("$.data[1].eventType").value("PAYMENT_SUCCESS"))
                .andExpect(jsonPath("$.data[1].fromStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data[1].toStatus").value("PAID"))
                .andExpect(jsonPath("$.data[1].eventId").value("pay-evt-1001"))
                .andExpect(jsonPath("$.data[1].payload").value("{\"handleResult\":\"processed\"}"));
    }

    @Test
    void adminOrderEventsFallsBackToOrderStateWhenNoRecordedEvents() throws Exception {
        saveOrder(namedOrder("ORD-EVT-CANCEL-1001", 852L, "leo", "SKU-EVT-B", OrderStatus.CANCELLED),
                LocalDateTime.of(2026, 5, 20, 8, 0));

        mockMvc.perform(get("/api/admin/orders/ORD-EVT-CANCEL-1001/events")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].toStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data[1].fromStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data[1].toStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data[1].eventType").doesNotExist())
                .andExpect(jsonPath("$.data[1].eventId").doesNotExist());
    }

    @Test
    void adminOrderRoutesAreReadOnly() {
        // Phase 2 forbids admin order status mutation: every /api/admin/orders route must be read-only.
        EnumSet<RequestMethod> writeMethods =
                EnumSet.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

        requestMappingHandlerMapping.getHandlerMethods().forEach((info, handlerMethod) -> {
            var pathPatterns = info.getPathPatternsCondition();
            if (pathPatterns == null) {
                return;
            }
            boolean touchesAdminOrders = pathPatterns.getPatternValues().stream()
                    .anyMatch(pattern -> pattern.startsWith("/api/admin/orders"));
            if (touchesAdminOrders) {
                assertThat(info.getMethodsCondition().getMethods())
                        .as("admin order route %s must be read-only", pathPatterns.getPatternValues())
                        .doesNotContainAnyElementsOf(writeMethods);
            }
        });
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

    private Order namedOrder(String orderNo, Long userId, String username, String productId, OrderStatus status) {
        Order order = new Order(
                orderNo,
                userId,
                username,
                productId,
                "Admin Order Product",
                2,
                new BigDecimal("19.90"),
                new BigDecimal("39.80"),
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

    private void saveEvent(
            String eventId,
            String orderNo,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String payload,
            LocalDateTime createdAt) {
        OrderEvent event = orderEventRepository.saveAndFlush(new OrderEvent(
                eventId, orderNo, OrderEventType.PAYMENT_SUCCESS, fromStatus, toStatus, payload));
        jdbcTemplate.update(
                "update order_events set created_at = ? where event_id = ?",
                Timestamp.valueOf(createdAt),
                event.getEventId());
    }
}
