package com.minimall.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecordSourceType;
import com.minimall.inventory.dto.InventoryChangeRequest;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:internal_inventory_controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class InternalInventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryRecordRepository inventoryRecordRepository;

    @BeforeEach
    void setUp() {
        inventoryRecordRepository.deleteAll();
        inventoryRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        inventoryRecordRepository.deleteAll();
        inventoryRepository.deleteAll();
    }

    @Test
    void deductUpdatesInventoryAndCreatesRecord() throws Exception {
        inventoryRepository.saveAndFlush(new Inventory("SKU-STOCK-1", 10, 0));
        InventoryChangeRequest request = new InventoryChangeRequest("ORDER-DED-1", "SKU-STOCK-1", 3);

        mockMvc.perform(post("/internal/inventories/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.productId").value("SKU-STOCK-1"))
                .andExpect(jsonPath("$.data.availableStock").value(7))
                .andExpect(jsonPath("$.data.lockedStock").value(3))
                .andExpect(jsonPath("$.data.stockState").value("IN_STOCK"));

        assertThat(inventoryRecordRepository.findByOrderNoAndChangeType("ORDER-DED-1", InventoryChangeType.DEDUCT))
                .isPresent()
                .get()
                .satisfies(record -> {
                    assertThat(record.getProductId()).isEqualTo("SKU-STOCK-1");
                    assertThat(record.getOrderNo()).isEqualTo("ORDER-DED-1");
                    assertThat(record.getRequestId()).isEqualTo("ORDER-DED-1");
                    assertThat(record.getSourceType()).isEqualTo(InventoryRecordSourceType.ORDER_DEDUCT);
                    assertThat(record.getReferenceNo()).isEqualTo("ORDER-DED-1");
                    assertThat(record.getReason()).isNull();
                    assertThat(record.getAdminUserId()).isNull();
                    assertThat(record.getAdminUsername()).isNull();
                    assertThat(record.getQuantity()).isEqualTo(3);
                });
    }

    @Test
    void insufficientInventoryReturnsConflictWithoutRecord() throws Exception {
        inventoryRepository.saveAndFlush(new Inventory("SKU-STOCK-2", 2, 0));
        InventoryChangeRequest request = new InventoryChangeRequest("ORDER-DED-2", "SKU-STOCK-2", 3);

        mockMvc.perform(post("/internal/inventories/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()))
                .andExpect(jsonPath("$.message").value("Insufficient inventory"));

        Inventory inventory = inventoryRepository.findByProductId("SKU-STOCK-2").orElseThrow();
        assertThat(inventory.getAvailableStock()).isEqualTo(2);
        assertThat(inventory.getLockedStock()).isZero();
        assertThat(inventoryRecordRepository.existsByOrderNoAndChangeType("ORDER-DED-2", InventoryChangeType.DEDUCT))
                .isFalse();
    }

    @Test
    void duplicateDeductRequestIsIdempotent() throws Exception {
        inventoryRepository.saveAndFlush(new Inventory("SKU-STOCK-3", 10, 0));
        InventoryChangeRequest request = new InventoryChangeRequest("ORDER-IDEM-1", "SKU-STOCK-3", 4);
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/internal/inventories/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableStock").value(6))
                .andExpect(jsonPath("$.data.lockedStock").value(4));

        mockMvc.perform(post("/internal/inventories/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableStock").value(6))
                .andExpect(jsonPath("$.data.lockedStock").value(4));

        assertThat(inventoryRecordRepository.findByProductId("SKU-STOCK-3"))
                .hasSize(1)
                .allSatisfy(record -> assertThat(record.getChangeType()).isEqualTo(InventoryChangeType.DEDUCT));
    }

    @Test
    void releaseUpdatesInventoryAndCreatesRecord() throws Exception {
        inventoryRepository.saveAndFlush(new Inventory("SKU-STOCK-4", 3, 4));
        InventoryChangeRequest request = new InventoryChangeRequest("ORDER-REL-1", "SKU-STOCK-4", 2);

        mockMvc.perform(post("/internal/inventories/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.availableStock").value(5))
                .andExpect(jsonPath("$.data.lockedStock").value(2));

        assertThat(inventoryRecordRepository.findByOrderNoAndChangeType("ORDER-REL-1", InventoryChangeType.RELEASE))
                .isPresent()
                .get()
                .satisfies(record -> {
                    assertThat(record.getQuantity()).isEqualTo(2);
                    assertThat(record.getRequestId()).isEqualTo("ORDER-REL-1");
                    assertThat(record.getSourceType()).isEqualTo(InventoryRecordSourceType.ORDER_RELEASE);
                    assertThat(record.getReferenceNo()).isEqualTo("ORDER-REL-1");
                });
    }

    @Test
    void concurrentDeductDoesNotCreateNegativeInventory() throws Exception {
        inventoryRepository.saveAndFlush(new Inventory("SKU-STOCK-5", 5, 0));
        int requestCount = 10;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        List<Future<Integer>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                String orderNo = "ORDER-CONC-" + i;
                futures.add(executorService.submit(deductRequest(orderNo, ready, start)));
            }

            ready.await();
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }

            assertThat(statuses).containsOnly(200, 409);
            assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(5);
            assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(5);

            Inventory inventory = inventoryRepository.findByProductId("SKU-STOCK-5").orElseThrow();
            assertThat(inventory.getAvailableStock()).isZero();
            assertThat(inventory.getLockedStock()).isEqualTo(5);
            assertThat(inventoryRecordRepository.findByProductId("SKU-STOCK-5")).hasSize(5);
        } finally {
            executorService.shutdownNow();
        }
    }

    private Callable<Integer> deductRequest(String orderNo, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            InventoryChangeRequest request = new InventoryChangeRequest(orderNo, "SKU-STOCK-5", 1);
            return mockMvc.perform(post("/internal/inventories/deduct")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };
    }
}
