package com.minimall.inventory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.audit.AdminAuditAction;
import com.minimall.common.core.audit.AdminAuditLogWriteRequest;
import com.minimall.common.core.audit.AdminAuditResourceType;
import com.minimall.common.core.audit.AdminAuditWriter;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryRecordSourceType;
import com.minimall.inventory.domain.InventoryStatus;
import com.minimall.inventory.dto.AdjustInventoryRequest;
import com.minimall.inventory.dto.AdminInventoryResponse;
import com.minimall.inventory.dto.InitializeInventoryRequest;
import com.minimall.inventory.dto.InventoryChangeRequest;
import com.minimall.inventory.dto.InventoryResponse;
import com.minimall.inventory.dto.UpdateSafetyStockRequest;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryCommandService {

    private final InventoryRepository inventoryRepository;
    private final InventoryRecordRepository inventoryRecordRepository;
    private final InventoryQueryService inventoryQueryService;
    private final AdminAuditWriter adminAuditWriter;
    private final ObjectMapper objectMapper;

    public InventoryCommandService(
            InventoryRepository inventoryRepository,
            InventoryRecordRepository inventoryRecordRepository,
            InventoryQueryService inventoryQueryService,
            AdminAuditWriter adminAuditWriter,
            ObjectMapper objectMapper) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryRecordRepository = inventoryRecordRepository;
        this.inventoryQueryService = inventoryQueryService;
        this.adminAuditWriter = adminAuditWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InventoryResponse deduct(InventoryChangeRequest request) {
        return changeInventory(request, InventoryChangeType.DEDUCT);
    }

    @Transactional
    public InventoryResponse release(InventoryChangeRequest request) {
        return changeInventory(request, InventoryChangeType.RELEASE);
    }

    @Transactional
    public AdminInventoryResponse initialize(
            InitializeInventoryRequest request, InventoryAdminAuditContext auditContext) {
        if (inventoryRepository.existsByProductId(request.productId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Inventory already initialized");
        }

        Inventory inventory = new Inventory(request.productId(), request.initialStock(), 0);
        inventory.setSafetyStock(request.safetyStock());
        Inventory saved = inventoryRepository.saveAndFlush(inventory);

        if (request.initialStock() > 0) {
            inventoryRecordRepository.saveAndFlush(new InventoryRecord(
                    saved.getProductId(),
                    null,
                    InventoryChangeType.ADJUST_INCREASE,
                    request.initialStock(),
                    null,
                    "Initialize inventory",
                    auditContext.adminUserId(),
                    auditContext.adminUsername(),
                    InventoryRecordSourceType.ADMIN_INITIALIZE,
                    saved.getProductId()));
        }

        AdminInventoryResponse response = AdminInventoryResponse.from(saved);
        writeAudit(
                auditContext,
                AdminAuditAction.INVENTORY_INITIALIZE,
                saved.getProductId(),
                saved.getProductId(),
                null,
                response,
                "Initialize inventory " + saved.getProductId() + " at " + request.initialStock());
        return response;
    }

    @Transactional
    public AdminInventoryResponse adjust(
            String productId, AdjustInventoryRequest request, InventoryAdminAuditContext auditContext) {
        int delta = request.delta();
        if (delta == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "delta must not be zero");
        }

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Inventory not found"));

        // Idempotency: a repeated requestId returns the current state without re-applying.
        if (inventoryRecordRepository
                .existsBySourceTypeAndRequestId(InventoryRecordSourceType.ADMIN_ADJUSTMENT, request.requestId())) {
            return AdminInventoryResponse.from(inventory);
        }

        if (inventory.getAvailableStock() + delta < 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "Insufficient inventory");
        }

        AdminInventoryResponse before = AdminInventoryResponse.from(inventory);
        inventory.adjustAvailableStock(delta);
        Inventory saved = inventoryRepository.saveAndFlush(inventory);

        InventoryChangeType changeType =
                delta > 0 ? InventoryChangeType.ADJUST_INCREASE : InventoryChangeType.ADJUST_DECREASE;
        inventoryRecordRepository.saveAndFlush(new InventoryRecord(
                saved.getProductId(),
                null,
                changeType,
                Math.abs(delta),
                request.requestId(),
                request.reason(),
                auditContext.adminUserId(),
                auditContext.adminUsername(),
                InventoryRecordSourceType.ADMIN_ADJUSTMENT,
                request.requestId()));

        AdminInventoryResponse after = AdminInventoryResponse.from(saved);
        writeAudit(
                auditContext,
                AdminAuditAction.INVENTORY_ADJUST,
                saved.getProductId(),
                request.requestId(),
                before,
                after,
                "Adjust inventory " + saved.getProductId() + " by " + delta + " (" + request.reason() + ")");
        return after;
    }

    @Transactional
    public AdminInventoryResponse updateSafetyStock(
            String productId, UpdateSafetyStockRequest request, InventoryAdminAuditContext auditContext) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Inventory not found"));

        AdminInventoryResponse before = AdminInventoryResponse.from(inventory);
        inventory.setSafetyStock(request.safetyStock());
        Inventory saved = inventoryRepository.saveAndFlush(inventory);

        AdminInventoryResponse after = AdminInventoryResponse.from(saved);
        writeAudit(
                auditContext,
                AdminAuditAction.INVENTORY_ADJUST,
                saved.getProductId(),
                saved.getProductId(),
                before,
                after,
                "Update inventory " + saved.getProductId() + " safety stock to " + request.safetyStock());
        return after;
    }

    private void writeAudit(
            InventoryAdminAuditContext auditContext,
            AdminAuditAction action,
            String resourceId,
            String referenceNo,
            AdminInventoryResponse before,
            AdminInventoryResponse after,
            String summary) {
        adminAuditWriter.write(new AdminAuditLogWriteRequest(
                auditContext.adminUserId(),
                auditContext.adminUsername(),
                action,
                AdminAuditResourceType.INVENTORY,
                resourceId,
                auditContext.requestId(),
                null,
                referenceNo,
                toSnapshot(before),
                toSnapshot(after),
                auditContext.ip(),
                auditContext.userAgent(),
                summary));
    }

    private JsonNode toSnapshot(AdminInventoryResponse response) {
        return response == null ? null : objectMapper.valueToTree(response);
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
            default -> throw new IllegalArgumentException("Unsupported order-flow change type: " + changeType);
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
