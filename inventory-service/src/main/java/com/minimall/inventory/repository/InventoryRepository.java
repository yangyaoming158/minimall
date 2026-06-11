package com.minimall.inventory.repository;

import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository
        extends JpaRepository<Inventory, Long>, JpaSpecificationExecutor<Inventory> {

    Optional<Inventory> findByProductId(String productId);

    List<Inventory> findByProductIdIn(Collection<String> productIds);

    boolean existsByProductId(String productId);

    /**
     * Structured low-stock query for the admin inventory API and later AI
     * replenishment analysis: active products whose available stock has reached
     * or fallen below a positive safety threshold. Keeps callers off direct
     * database access.
     */
    @Query("""
            select inventory from Inventory inventory
             where inventory.status = :status
               and inventory.safetyStock > 0
               and inventory.availableStock <= inventory.safetyStock
             order by inventory.productId asc
            """)
    Page<Inventory> findLowStock(@Param("status") InventoryStatus status, Pageable pageable);

    @Query("""
            select count(inventory) from Inventory inventory
             where inventory.status = :status
               and inventory.safetyStock > 0
               and inventory.availableStock <= inventory.safetyStock
            """)
    long countLowStock(@Param("status") InventoryStatus status);

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
