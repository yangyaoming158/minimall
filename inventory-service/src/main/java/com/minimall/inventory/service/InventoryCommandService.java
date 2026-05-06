package com.minimall.inventory.service;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryStatus;
import com.minimall.inventory.dto.InventoryChangeRequest;
import com.minimall.inventory.dto.InventoryResponse;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryCommandService {

    private final InventoryRepository inventoryRepository;
    private final InventoryRecordRepository inventoryRecordRepository;
    private final InventoryQueryService inventoryQueryService;

    public InventoryCommandService(
            InventoryRepository inventoryRepository,
            InventoryRecordRepository inventoryRecordRepository,
            InventoryQueryService inventoryQueryService) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryRecordRepository = inventoryRecordRepository;
        this.inventoryQueryService = inventoryQueryService;
    }

    @Transactional
    public InventoryResponse deduct(InventoryChangeRequest request) {
        return changeInventory(request, InventoryChangeType.DEDUCT);
    }

    @Transactional
    public InventoryResponse release(InventoryChangeRequest request) {
        return changeInventory(request, InventoryChangeType.RELEASE);
    }

    private InventoryResponse changeInventory(InventoryChangeRequest request, InventoryChangeType changeType) {
        InventoryRecord existingRecord = inventoryRecordRepository
                .findByOrderNoAndChangeType(request.orderNo(), changeType)
                .orElse(null);
        if (existingRecord != null) {
            return inventoryQueryService.detail(existingRecord.getProductId());
        }

        int updatedRows = switch (changeType) {
            case DEDUCT -> inventoryRepository.deductAvailableStock(
                    request.productId(), request.quantity(), InventoryStatus.ACTIVE);
            case RELEASE -> inventoryRepository.releaseLockedStock(
                    request.productId(), request.quantity(), InventoryStatus.ACTIVE);
        };

        if (updatedRows == 0) {
            assertInventoryExists(request.productId());
            throw stockChangeException(changeType);
        }

        inventoryRecordRepository.saveAndFlush(new InventoryRecord(
                request.productId(), request.orderNo(), changeType, request.quantity()));
        return inventoryQueryService.detail(request.productId());
    }

    private void assertInventoryExists(String productId) {
        if (!inventoryRepository.existsByProductId(productId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Inventory not found");
        }
    }

    private BusinessException stockChangeException(InventoryChangeType changeType) {
        if (changeType == InventoryChangeType.DEDUCT) {
            return new BusinessException(ErrorCode.CONFLICT, "Insufficient inventory");
        }
        return new BusinessException(ErrorCode.BAD_REQUEST, "Insufficient locked inventory");
    }
}