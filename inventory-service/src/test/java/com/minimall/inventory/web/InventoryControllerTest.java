package com.minimall.inventory.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minimall.common.core.exception.ErrorCode;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryStatus;
import com.minimall.inventory.repository.InventoryRepository;
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
        "spring.datasource.url=jdbc:h2:mem:inventory_controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryRepository inventoryRepository;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
    }

    @Test
    void detailReturnsStableApiResponseForAvailableInventory() throws Exception {
        inventoryRepository.saveAndFlush(new Inventory("SKU-INV-API-1", 12, 3));

        mockMvc.perform(get("/api/inventories/SKU-INV-API-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.SUCCESS.getMessage()))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.productId").value("SKU-INV-API-1"))
                .andExpect(jsonPath("$.data.availableStock").value(12))
                .andExpect(jsonPath("$.data.lockedStock").value(3))
                .andExpect(jsonPath("$.data.stockState").value("IN_STOCK"));
    }

    @Test
    void detailReturnsOutOfStockWhenActiveInventoryHasNoAvailableStock() throws Exception {
        inventoryRepository.saveAndFlush(new Inventory("SKU-INV-API-2", 0, 8));

        mockMvc.perform(get("/api/inventories/SKU-INV-API-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.productId").value("SKU-INV-API-2"))
                .andExpect(jsonPath("$.data.availableStock").value(0))
                .andExpect(jsonPath("$.data.lockedStock").value(8))
                .andExpect(jsonPath("$.data.stockState").value("OUT_OF_STOCK"));
    }

    @Test
    void detailReturnsInactiveWhenInventoryIsInactive() throws Exception {
        Inventory inventory = new Inventory("SKU-INV-API-3", 20, 0);
        inventory.setStatus(InventoryStatus.INACTIVE);
        inventoryRepository.saveAndFlush(inventory);

        mockMvc.perform(get("/api/inventories/SKU-INV-API-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.productId").value("SKU-INV-API-3"))
                .andExpect(jsonPath("$.data.stockState").value("INACTIVE"));
    }

    @Test
    void missingInventoryReturnsNotFoundApiResponse() throws Exception {
        mockMvc.perform(get("/api/inventories/MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("Inventory not found"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
