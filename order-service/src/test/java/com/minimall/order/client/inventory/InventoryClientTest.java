package com.minimall.order.client.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

class InventoryClientTest {

    private final RestTemplate restTemplate = restTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    private final InventoryClient inventoryClient = new InventoryClient(restTemplate);

    @Test
    void deductPostsInternalRequestAndReturnsInventorySnapshot() {
        server.expect(once(), requestTo("http://inventory-service/internal/inventories/deduct"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"orderNo":"ORD-1001","productId":"SKU-3001","quantity":2}
                        """))
                .andRespond(withSuccess("""
                        {"success":true,"code":"00000","message":"Success","data":{"productId":"SKU-3001","availableStock":8,"lockedStock":2,"stockState":"IN_STOCK"}}
                        """, MediaType.APPLICATION_JSON));

        InventorySnapshot snapshot = inventoryClient.deduct(new InventoryDeductRequest("ORD-1001", "SKU-3001", 2));

        assertThat(snapshot.productId()).isEqualTo("SKU-3001");
        assertThat(snapshot.availableStock()).isEqualTo(8);
        assertThat(snapshot.lockedStock()).isEqualTo(2);
        assertThat(snapshot.stockState()).isEqualTo(InventoryStockState.IN_STOCK);
        server.verify();
    }

    @Test
    void deductMapsHttpConflictToStableBusinessException() {
        server.expect(once(), requestTo("http://inventory-service/internal/inventories/deduct"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> inventoryClient.deduct(new InventoryDeductRequest("ORD-1002", "SKU-LOW", 99)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.getMessage()).isEqualTo("Insufficient inventory");
                });
        server.verify();
    }

    @Test
    void deductMapsUnsuccessfulConflictBodyToStableBusinessException() {
        server.expect(once(), requestTo("http://inventory-service/internal/inventories/deduct"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"success":false,"code":"40900","message":"downstream detail","data":null}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> inventoryClient.deduct(new InventoryDeductRequest("ORD-1003", "SKU-LOW", 4)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.getMessage()).isEqualTo("Insufficient inventory");
                });
        server.verify();
    }

    @Test
    void deductMapsHttpNotFoundToStableBusinessException() {
        server.expect(once(), requestTo("http://inventory-service/internal/inventories/deduct"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> inventoryClient.deduct(new InventoryDeductRequest("ORD-1004", "SKU-MISSING", 1)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    assertThat(exception.getMessage()).isEqualTo("Inventory not found");
                });
        server.verify();
    }

    @Test
    void deductMapsDownstreamFailureWithoutLeakingDetails() {
        server.expect(once(), requestTo("http://inventory-service/internal/inventories/deduct"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body("database stack trace"));

        assertThatThrownBy(() -> inventoryClient.deduct(new InventoryDeductRequest("ORD-1005", "SKU-ERR", 1)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("Inventory deduct failed");
        });
        server.verify();
    }

    @Test
    void releasePostsInternalRequestAndReturnsInventorySnapshot() {
        server.expect(once(), requestTo("http://inventory-service/internal/inventories/release"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"orderNo":"ORD-2001","productId":"SKU-3001","quantity":2}
                        """))
                .andRespond(withSuccess("""
                        {"success":true,"code":"00000","message":"Success","data":{"productId":"SKU-3001","availableStock":10,"lockedStock":0,"stockState":"IN_STOCK"}}
                        """, MediaType.APPLICATION_JSON));

        InventorySnapshot snapshot = inventoryClient.release(new InventoryDeductRequest("ORD-2001", "SKU-3001", 2));

        assertThat(snapshot.productId()).isEqualTo("SKU-3001");
        assertThat(snapshot.availableStock()).isEqualTo(10);
        assertThat(snapshot.lockedStock()).isZero();
        assertThat(snapshot.stockState()).isEqualTo(InventoryStockState.IN_STOCK);
        server.verify();
    }

    @Test
    void releaseMapsUnsuccessfulBadRequestBodyToStableBusinessException() {
        server.expect(once(), requestTo("http://inventory-service/internal/inventories/release"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"success":false,"code":"40000","message":"downstream detail","data":null}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> inventoryClient.release(new InventoryDeductRequest("ORD-2002", "SKU-LOW-LOCKED", 3)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("Insufficient locked inventory");
                });
        server.verify();
    }

    @Test
    void releaseMapsHttpNotFoundToStableBusinessException() {
        server.expect(once(), requestTo("http://inventory-service/internal/inventories/release"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> inventoryClient.release(new InventoryDeductRequest("ORD-2003", "SKU-MISSING", 1)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    assertThat(exception.getMessage()).isEqualTo("Inventory not found");
                });
        server.verify();
    }

    @Test
    void releaseMapsDownstreamFailureWithoutLeakingDetails() {
        server.expect(once(), requestTo("http://inventory-service/internal/inventories/release"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body("http://inventory-service/internal stack trace"));

        assertThatThrownBy(() -> inventoryClient.release(new InventoryDeductRequest("ORD-2004", "SKU-ERR", 1)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("取消失败，请稍后重试");
                });
        server.verify();
    }

    private RestTemplate restTemplate() {
        RestTemplate template = new RestTemplate();
        template.setUriTemplateHandler(new DefaultUriBuilderFactory("http://inventory-service"));
        return template;
    }
}
