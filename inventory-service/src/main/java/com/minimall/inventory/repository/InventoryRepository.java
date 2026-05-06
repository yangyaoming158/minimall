package com.minimall.inventory.repository;

import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(String productId);

    boolean existsByProductId(String productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Inventory inventory
               set inventory.availableStock = inventory.availableStock - :quantity,
                   inventory.lockedStock = inventory.lockedStock + :quantity
             where inventory.productId = :productId
               and inventory.status = :status
               and inventory.availableStock >= :quantity
            """)
    int deductAvailableStock(
            @Param("productId") String productId,
            @Param("quantity") int quantity,
            @Param("status") InventoryStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Inventory inventory
               set inventory.availableStock = inventory.availableStock + :quantity,
                   inventory.lockedStock = inventory.lockedStock - :quantity
             where inventory.productId = :productId
               and inventory.status = :status
               and inventory.lockedStock >= :quantity
            """)
    int releaseLockedStock(
            @Param("productId") String productId,
            @Param("quantity") int quantity,
            @Param("status") InventoryStatus status);
}