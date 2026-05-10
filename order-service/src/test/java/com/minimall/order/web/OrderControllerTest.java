package com.minimall.order.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minimall.common.auth.constants.AuthHeaders;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.order.client.inventory.InventoryClient;
import com.minimall.order.client.inventory.InventoryDeductRequest;
import com.minimall.order.client.inventory.InventorySnapshot;
import com.minimall.order.client.inventory.InventoryStockState;
import com.minimall.order.client.product.ProductSnapshot;
import com.minimall.order.client.product.ProductStatus;
import com.minimall.order.client.product.ProductValidationService;
import com.minimall.order.domain.Order;
import com.minimall.order.domain.OrderStateMachine;
import com.minimall.order.domain.OrderStatus;
import com.minimall.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
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
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "minimall.order.payment-expire-seconds=900"
})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private ProductValidationService productValidationService;

    @MockBean
    private InventoryClient inventoryClient;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private final ValueOperations<String, String> redisValueOperations = org.mockito.Mockito.mock(ValueOperations.class);

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        reset(productValidationService, inventoryClient, redisTemplate, redisValueOperations);
        given(redisTemplate.opsForValue()).willReturn(redisValueOperations);
        given(redisValueOperations.setIfAbsent(any(String.class), any(String.class), any(Duration.class)))
                .willReturn(true);
    }

    @Test
    void createOrderValidatesProductDeductsInventoryAndPersistsPendingOrder() throws Exception {
        given(productValidationService.validateSellable("SKU-CREATE-1001"))
                .willReturn(new ProductSnapshot(
                        "SKU-CREATE-1001",
                        "Create Product",
                        new BigDecimal("19.90"),
                        ProductStatus.ON_SHELF));
        given(inventoryClient.deduct(any(InventoryDeductRequest.class)))
                .willReturn(new InventorySnapshot("SKU-CREATE-1001", 8, 2, InventoryStockState.IN_STOCK));

        LocalDateTime before = LocalDateTime.now();
        mockMvc.perform(post("/api/orders")
                        .header(AuthHeaders.USER_ID, "201")
                        .header(AuthHeaders.USERNAME, "erin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"SKU-CREATE-1001","quantity":2,"idempotencyKey":"idem-create-1001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.username").doesNotExist())
                .andExpect(jsonPath("$.data.idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.data.orderNo", startsWith("ORD")))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.expireAt").exists())
                .andExpect(jsonPath("$.data.totalAmount").value(39.80))
                .andExpect(jsonPath("$.data.productId").value("SKU-CREATE-1001"))
                .andExpect(jsonPath("$.data.quantity").value(2));
        LocalDateTime after = LocalDateTime.now();

        assertThat(orderRepository.findAll())
                .singleElement()
                .satisfies(order -> {
                    assertThat(order.getOrderNo()).startsWith("ORD");
                    assertThat(order.getUserId()).isEqualTo(201L);
                    assertThat(order.getUsername()).isEqualTo("erin");
                    assertThat(order.getProductId()).isEqualTo("SKU-CREATE-1001");
                    assertThat(order.getProductName()).isEqualTo("Create Product");
                    assertThat(order.getQuantity()).isEqualTo(2);
                    assertThat(order.getUnitPrice()).isEqualByComparingTo("19.90");
                    assertThat(order.getTotalAmount()).isEqualByComparingTo("39.80");
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
                    assertThat(order.getIdempotencyKey()).isEqualTo("idem-create-1001");
                    assertThat(order.getExpireAt()).isBetween(before.plusSeconds(900), after.plusSeconds(900));
                });

        Order saved = orderRepository.findAll().get(0);
        ArgumentCaptor<InventoryDeductRequest> inventoryRequestCaptor = ArgumentCaptor.forClass(InventoryDeductRequest.class);
        verify(productValidationService).validateSellable("SKU-CREATE-1001");
        verify(inventoryClient).deduct(inventoryRequestCaptor.capture());
        assertThat(inventoryRequestCaptor.getValue().orderNo()).isEqualTo(saved.getOrderNo());
        assertThat(inventoryRequestCaptor.getValue().productId()).isEqualTo("SKU-CREATE-1001");
        assertThat(inventoryRequestCaptor.getValue().quantity()).isEqualTo(2);
    }

    @Test
    void createOrderReplayReturnsExistingOrderWithoutDeductingAgain() throws Exception {
        given(productValidationService.validateSellable("SKU-REPLAY-1001"))
                .willReturn(new ProductSnapshot(
                        "SKU-REPLAY-1001",
                        "Replay Product",
                        new BigDecimal("11.50"),
                        ProductStatus.ON_SHELF));
        given(inventoryClient.deduct(any(InventoryDeductRequest.class)))
                .willReturn(new InventorySnapshot("SKU-REPLAY-1001", 4, 1, InventoryStockState.IN_STOCK));

        mockMvc.perform(post("/api/orders")
                        .header(AuthHeaders.USER_ID, "205")
                        .header(AuthHeaders.USERNAME, "ivy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"SKU-REPLAY-1001","quantity":1,"idempotencyKey":"idem-replay-1001"}
                                """))
                .andExpect(status().isOk());
        Order saved = orderRepository.findAll().get(0);

        mockMvc.perform(post("/api/orders")
                        .header(AuthHeaders.USER_ID, "205")
                        .header(AuthHeaders.USERNAME, "ivy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"SKU-REPLAY-1001","quantity":1,"idempotencyKey":"idem-replay-1001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNo").value(saved.getOrderNo()))
                .andExpect(jsonPath("$.data.totalAmount").value(11.50))
                .andExpect(jsonPath("$.data.productId").value("SKU-REPLAY-1001"));

        assertThat(orderRepository.findAll()).hasSize(1);
        verify(productValidationService, times(1)).validateSellable("SKU-REPLAY-1001");
        verify(inventoryClient, times(1)).deduct(any(InventoryDeductRequest.class));
    }

    @Test
    void createOrderInFlightDuplicateReturnsRetryableConflict() throws Exception {
        given(redisValueOperations.setIfAbsent(any(String.class), any(String.class), any(Duration.class)))
                .willReturn(false);

        mockMvc.perform(post("/api/orders")
                        .header(AuthHeaders.USER_ID, "206")
                        .header(AuthHeaders.USERNAME, "jane")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"SKU-IN-FLIGHT","quantity":1,"idempotencyKey":"idem-in-flight"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()))
                .andExpect(jsonPath("$.message").value("Order creation is in progress, please retry"));

        verifyNoInteractions(productValidationService, inventoryClient);
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void createOrderProductBusinessFailureDoesNotDeductInventoryOrPersistOrder() throws Exception {
        given(productValidationService.validateSellable("SKU-OFF-SHELF"))
                .willThrow(new BusinessException(ErrorCode.BAD_REQUEST, "Product is off shelf"));

        mockMvc.perform(post("/api/orders")
                        .header(AuthHeaders.USER_ID, "202")
                        .header(AuthHeaders.USERNAME, "frank")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"SKU-OFF-SHELF","quantity":1,"idempotencyKey":"idem-off-shelf"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("Product is off shelf"));

        verify(productValidationService).validateSellable("SKU-OFF-SHELF");
        verifyNoInteractions(inventoryClient);
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void createOrderMissingProductDoesNotDeductInventoryOrPersistOrder() throws Exception {
        given(productValidationService.validateSellable("SKU-MISSING"))
                .willThrow(new BusinessException(ErrorCode.NOT_FOUND, "Product not found"));

        mockMvc.perform(post("/api/orders")
                        .header(AuthHeaders.USER_ID, "207")
                        .header(AuthHeaders.USERNAME, "louis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"SKU-MISSING","quantity":1,"idempotencyKey":"idem-missing-product"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("Product not found"));

        verify(productValidationService).validateSellable("SKU-MISSING");
        verifyNoInteractions(inventoryClient);
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void createOrderInventoryBusinessFailureDoesNotPersistOrder() throws Exception {
        given(productValidationService.validateSellable("SKU-LOW-STOCK"))
                .willReturn(new ProductSnapshot(
                        "SKU-LOW-STOCK",
                        "Low Stock Product",
                        new BigDecimal("29.90"),
                        ProductStatus.ON_SHELF));
        given(inventoryClient.deduct(any(InventoryDeductRequest.class)))
                .willThrow(new BusinessException(ErrorCode.CONFLICT, "Insufficient inventory"));

        mockMvc.perform(post("/api/orders")
                        .header(AuthHeaders.USER_ID, "203")
                        .header(AuthHeaders.USERNAME, "gina")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"SKU-LOW-STOCK","quantity":5,"idempotencyKey":"idem-low-stock"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()))
                .andExpect(jsonPath("$.message").value("Insufficient inventory"));

        verify(productValidationService).validateSellable("SKU-LOW-STOCK");
        verify(inventoryClient).deduct(any(InventoryDeductRequest.class));
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void createOrderMissingFieldsReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(AuthHeaders.USER_ID, "204")
                        .header(AuthHeaders.USERNAME, "henry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.message", containsString("productId: productId must not be blank")))
                .andExpect(jsonPath("$.message", containsString("quantity: quantity must not be null")))
                .andExpect(jsonPath("$.message", containsString("idempotencyKey: idempotencyKey must not be blank")));
    }

    @Test
    void createOrderMissingAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"SKU-CREATE-1002","quantity":1,"idempotencyKey":"idem-create-1002"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    void cancelPendingOrderReturnsApiResponseAndReleasesInventory() throws Exception {
        orderRepository.saveAndFlush(order("ORD-CANCEL-API-1001", 301L, "idem-cancel-api-1001"));
        given(inventoryClient.release(any(InventoryDeductRequest.class)))
                .willReturn(new InventorySnapshot("SKU-API-1001", 10, 0, InventoryStockState.IN_STOCK));

        mockMvc.perform(post("/api/orders/ORD-CANCEL-API-1001/cancel")
                        .header(AuthHeaders.USER_ID, "301")
                        .header(AuthHeaders.USERNAME, "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.orderNo").value("ORD-CANCEL-API-1001"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.userId").doesNotExist());

        Order saved = orderRepository.findByOrderNo("ORD-CANCEL-API-1001").orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        ArgumentCaptor<InventoryDeductRequest> releaseRequest = ArgumentCaptor.forClass(InventoryDeductRequest.class);
        verify(inventoryClient).release(releaseRequest.capture());
        assertThat(releaseRequest.getValue().orderNo()).isEqualTo("ORD-CANCEL-API-1001");
        assertThat(releaseRequest.getValue().productId()).isEqualTo("SKU-API-1001");
        assertThat(releaseRequest.getValue().quantity()).isEqualTo(2);
    }

    @Test
    void cancelAlreadyCancelledOrderReturnsSuccessWithoutReleasingInventoryAgain() throws Exception {
        Order order = order("ORD-CANCEL-API-1002", 302L, "idem-cancel-api-1002");
        new OrderStateMachine().transition(order, OrderStatus.CANCELLED, LocalDateTime.of(2026, 5, 10, 22, 0));
        orderRepository.saveAndFlush(order);

        mockMvc.perform(post("/api/orders/ORD-CANCEL-API-1002/cancel")
                        .header(AuthHeaders.USER_ID, "302")
                        .header(AuthHeaders.USERNAME, "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.orderNo").value("ORD-CANCEL-API-1002"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        verify(inventoryClient, never()).release(any(InventoryDeductRequest.class));
    }

    @Test
    void cancelSameOrderTwiceReleasesInventoryOnlyOnce() throws Exception {
        orderRepository.saveAndFlush(order("ORD-CANCEL-API-1005", 305L, "idem-cancel-api-1005"));
        given(inventoryClient.release(any(InventoryDeductRequest.class)))
                .willReturn(new InventorySnapshot("SKU-API-1001", 10, 0, InventoryStockState.IN_STOCK));

        mockMvc.perform(post("/api/orders/ORD-CANCEL-API-1005/cancel")
                        .header(AuthHeaders.USER_ID, "305")
                        .header(AuthHeaders.USERNAME, "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(post("/api/orders/ORD-CANCEL-API-1005/cancel")
                        .header(AuthHeaders.USER_ID, "305")
                        .header(AuthHeaders.USERNAME, "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        verify(inventoryClient, times(1)).release(any(InventoryDeductRequest.class));
        assertThat(orderRepository.findByOrderNo("ORD-CANCEL-API-1005").orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelInventoryReleaseFailureReturnsSafeMessageAndKeepsOrderPending() throws Exception {
        orderRepository.saveAndFlush(order("ORD-CANCEL-API-1006", 306L, "idem-cancel-api-1006"));
        given(inventoryClient.release(any(InventoryDeductRequest.class)))
                .willThrow(new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        "Cancellation failed, please retry",
                        new IllegalStateException("http://inventory-service/internal/inventories/release stack trace")));

        mockMvc.perform(post("/api/orders/ORD-CANCEL-API-1006/cancel")
                        .header(AuthHeaders.USER_ID, "306")
                        .header(AuthHeaders.USERNAME, "alice"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("Cancellation failed, please retry"))
                .andExpect(jsonPath("$.message", not(containsString("inventory-service"))))
                .andExpect(jsonPath("$.message", not(containsString("http://"))))
                .andExpect(jsonPath("$.message", not(containsString("stack trace"))));

        verify(inventoryClient, times(1)).release(any(InventoryDeductRequest.class));
        assertThat(orderRepository.findByOrderNo("ORD-CANCEL-API-1006").orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void cancelPaidOrderReturnsConflictApiResponse() throws Exception {
        Order order = order("ORD-CANCEL-API-1003", 303L, "idem-cancel-api-1003");
        new OrderStateMachine().transition(order, OrderStatus.PAID, LocalDateTime.of(2026, 5, 10, 22, 5));
        orderRepository.saveAndFlush(order);

        mockMvc.perform(post("/api/orders/ORD-CANCEL-API-1003/cancel")
                        .header(AuthHeaders.USER_ID, "303")
                        .header(AuthHeaders.USERNAME, "alice"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()))
                .andExpect(jsonPath("$.message").value("Order current status cannot be cancelled: PAID"));

        verify(inventoryClient, never()).release(any(InventoryDeductRequest.class));
    }

    @Test
    void cancelMissingAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/orders/ORD-CANCEL-API-1004/cancel"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    void cancelMissingOrderReturnsNotFoundApiResponse() throws Exception {
        mockMvc.perform(post("/api/orders/ORD-MISSING-CANCEL/cancel")
                        .header(AuthHeaders.USER_ID, "304")
                        .header(AuthHeaders.USERNAME, "alice"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("Order not found"));

        verify(inventoryClient, never()).release(any(InventoryDeductRequest.class));
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
