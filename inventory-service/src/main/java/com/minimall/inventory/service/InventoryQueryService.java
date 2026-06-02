package com.minimall.inventory.service;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryStatus;
import com.minimall.inventory.domain.StockState;
import com.minimall.inventory.dto.AdminInventoryResponse;
import com.minimall.inventory.dto.InventoryRecordResponse;
import com.minimall.inventory.dto.InventoryResponse;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InventoryQueryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryRecordRepository inventoryRecordRepository;

    public InventoryQueryService(
            InventoryRepository inventoryRepository,
            InventoryRecordRepository inventoryRecordRepository) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryRecordRepository = inventoryRecordRepository;
    }

    @Transactional(readOnly = true)
    public InventoryResponse detail(String productId) {
        return toResponse(getInventory(productId));
    }

    @Transactional(readOnly = true)
    public AdminInventoryResponse adminDetail(String productId) {
        return AdminInventoryResponse.from(getInventory(productId));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminInventoryResponse> adminList(
            String keyword, StockState stockState, Boolean lowStock, Pageable pageable) {
        Specification<Inventory> specification = adminInventorySpecification(keyword, stockState, lowStock);
        return PageResponse.from(
                inventoryRepository.findAll(specification, pageable).map(AdminInventoryResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminInventoryResponse> lowStock(Pageable pageable) {
        return PageResponse.from(inventoryRepository.findLowStock(InventoryStatus.ACTIVE, pageable)
                .map(AdminInventoryResponse::from));
    }

    @Transactional(readOnly = true)
    public List<InventoryRecordResponse> records(String productId) {
        getInventory(productId);
        return inventoryRecordRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId).stream()
                .map(InventoryRecordResponse::from)
                .toList();
    }

    private Inventory getInventory(String productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Inventory not found"));
    }

    private InventoryResponse toResponse(Inventory inventory) {
        return new InventoryResponse(
                inventory.getProductId(),
                inventory.getAvailableStock(),
                inventory.getLockedStock(),
                inventory.stockState());
    }

    private Specification<Inventory> adminInventorySpecification(
            String keyword, StockState stockState, Boolean lowStock) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                predicates.add(criteriaBuilder.like(root.get("productId"), "%" + keyword.trim() + "%"));
            }
            if (stockState != null) {
                switch (stockState) {
                    case IN_STOCK -> {
                        predicates.add(criteriaBuilder.equal(root.get("status"), InventoryStatus.ACTIVE));
                        predicates.add(criteriaBuilder.greaterThan(root.get("availableStock"), 0));
                    }
                    case OUT_OF_STOCK -> {
                        predicates.add(criteriaBuilder.equal(root.get("status"), InventoryStatus.ACTIVE));
                        predicates.add(criteriaBuilder.equal(root.get("availableStock"), 0));
                    }
                    case INACTIVE ->
                        predicates.add(criteriaBuilder.equal(root.get("status"), InventoryStatus.INACTIVE));
                }
            }
            if (Boolean.TRUE.equals(lowStock)) {
                predicates.add(criteriaBuilder.equal(root.get("status"), InventoryStatus.ACTIVE));
                predicates.add(criteriaBuilder.greaterThan(root.get("safetyStock"), 0));
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("availableStock"), root.get("safetyStock")));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
