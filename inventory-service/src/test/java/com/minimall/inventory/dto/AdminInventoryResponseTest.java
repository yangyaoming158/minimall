package com.minimall.inventory.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryStatus;
import com.minimall.inventory.domain.StockState;
import org.junit.jupiter.api.Test;

class AdminInventoryResponseTest {

    @Test
    void mapsThresholdAndFlagsLowStockWhenAtOrBelowThreshold() {
        Inventory inventory = new Inventory("SKU-ADMIN-1", 3, 2);
        inventory.setSafetyStock(5);

        AdminInventoryResponse response = AdminInventoryResponse.from(inventory);

        assertThat(response.productId()).isEqualTo("SKU-ADMIN-1");
        assertThat(response.availableStock()).isEqualTo(3);
        assertThat(response.lockedStock()).isEqualTo(2);
        assertThat(response.safetyStock()).isEqualTo(5);
        assertThat(response.status()).isEqualTo(InventoryStatus.ACTIVE);
        assertThat(response.stockState()).isEqualTo(StockState.IN_STOCK);
        assertThat(response.lowStock()).isTrue();
    }

    @Test
    void healthyStockIsNotFlaggedLow() {
        Inventory inventory = new Inventory("SKU-ADMIN-2", 50, 0);
        inventory.setSafetyStock(5);

        AdminInventoryResponse response = AdminInventoryResponse.from(inventory);

        assertThat(response.lowStock()).isFalse();
        assertThat(response.stockState()).isEqualTo(StockState.IN_STOCK);
    }

    @Test
    void zeroThresholdDisablesLowStockSignal() {
        Inventory inventory = new Inventory("SKU-ADMIN-3", 0, 0);

        AdminInventoryResponse response = AdminInventoryResponse.from(inventory);

        assertThat(response.safetyStock()).isEqualTo(0);
        assertThat(response.lowStock()).isFalse();
        assertThat(response.stockState()).isEqualTo(StockState.OUT_OF_STOCK);
    }

    @Test
    void inactiveInventoryIsNeitherLowStockNorInStock() {
        Inventory inventory = new Inventory("SKU-ADMIN-4", 1, 0);
        inventory.setSafetyStock(5);
        inventory.setStatus(InventoryStatus.INACTIVE);

        AdminInventoryResponse response = AdminInventoryResponse.from(inventory);

        assertThat(response.lowStock()).isFalse();
        assertThat(response.stockState()).isEqualTo(StockState.INACTIVE);
    }
}
