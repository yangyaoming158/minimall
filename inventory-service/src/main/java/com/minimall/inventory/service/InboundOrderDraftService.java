package com.minimall.inventory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.audit.AdminAuditAction;
import com.minimall.common.core.audit.AdminAuditLogWriteRequest;
import com.minimall.common.core.audit.AdminAuditResourceType;
import com.minimall.common.core.audit.AdminAuditWriter;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.domain.InboundOrder;
import com.minimall.inventory.domain.InboundOrderItem;
import com.minimall.inventory.domain.InboundOrderStatus;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryRecordSourceType;
import com.minimall.inventory.dto.CreateInboundOrderDraftItemRequest;
import com.minimall.inventory.dto.CreateInboundOrderDraftRequest;
import com.minimall.inventory.dto.InboundOrderResponse;
import com.minimall.inventory.repository.InboundOrderItemRepository;
import com.minimall.inventory.repository.InboundOrderRepository;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboundOrderDraftService {

    private static final int INBOUND_NO_RANDOM_LENGTH = 20;
    private static final int MAX_INBOUND_NO_GENERATION_ATTEMPTS = 5;
    private static final int SUMMARY_ITEM_LIMIT = 5;

    private final InboundOrderRepository inboundOrderRepository;
    private final InboundOrderItemRepository inboundOrderItemRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryRecordRepository inventoryRecordRepository;
    private final AdminAuditWriter adminAuditWriter;
    private final ObjectMapper objectMapper;

    public InboundOrderDraftService(
            InboundOrderRepository inboundOrderRepository,
            InboundOrderItemRepository inboundOrderItemRepository,
            InventoryRepository inventoryRepository,
            InventoryRecordRepository inventoryRecordRepository,
            AdminAuditWriter adminAuditWriter,
            ObjectMapper objectMapper) {
        this.inboundOrderRepository = inboundOrderRepository;
        this.inboundOrderItemRepository = inboundOrderItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryRecordRepository = inventoryRecordRepository;
        this.adminAuditWriter = adminAuditWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InboundOrderResponse createDraft(
            CreateInboundOrderDraftRequest request, InventoryAdminAuditContext auditContext) {
        List<CreateInboundOrderDraftItemRequest> items = normalizedItems(request);
        String inboundNo = generateInboundNo();
        InboundOrder order = inboundOrderRepository.saveAndFlush(
                new InboundOrder(inboundNo, auditContext.adminUserId(), auditContext.adminUsername()));

        List<InboundOrderItem> savedItems = inboundOrderItemRepository.saveAllAndFlush(items.stream()
                .map(item -> new InboundOrderItem(order.getInboundNo(), item.productId().trim(), item.quantity()))
                .toList());
        InboundOrderResponse response = InboundOrderResponse.from(order, savedItems);
        writeAudit(
                auditContext,
                AdminAuditAction.INBOUND_ORDER_CREATE,
                order.getInboundNo(),
                null,
                response,
                "Create inbound order draft " + order.getInboundNo() + " with " + itemSummary(savedItems));
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<InboundOrderResponse> list(InboundOrderStatus status, Pageable pageable) {
        Page<InboundOrder> orders = status == null
                ? inboundOrderRepository.findAll(defaultSort(pageable))
                : inboundOrderRepository.findByStatus(status, defaultSort(pageable));
        Map<String, List<InboundOrderItem>> itemsByInboundNo = itemsByInboundNo(orders.getContent());
        return PageResponse.from(orders.map(order -> InboundOrderResponse.from(
                order, itemsByInboundNo.getOrDefault(order.getInboundNo(), List.of()))));
    }

    @Transactional(readOnly = true)
    public InboundOrderResponse detail(String inboundNo) {
        InboundOrder order = getByInboundNo(inboundNo);
        return InboundOrderResponse.from(
                order, inboundOrderItemRepository.findByInboundNoOrderByIdAsc(order.getInboundNo()));
    }

    @Transactional
    public InboundOrderResponse cancel(String inboundNo, InventoryAdminAuditContext auditContext) {
        InboundOrder order = getByInboundNo(inboundNo);
        List<InboundOrderItem> items = inboundOrderItemRepository.findByInboundNoOrderByIdAsc(order.getInboundNo());
        if (order.getStatus() == InboundOrderStatus.CANCELLED) {
            return InboundOrderResponse.from(order, items);
        }
        if (order.getStatus() != InboundOrderStatus.DRAFT) {
            throw new BusinessException(ErrorCode.CONFLICT, "Only draft inbound orders can be cancelled");
        }
        InboundOrderResponse before = InboundOrderResponse.from(order, items);
        order.setStatus(InboundOrderStatus.CANCELLED);
        InboundOrder saved = inboundOrderRepository.saveAndFlush(order);
        InboundOrderResponse after = InboundOrderResponse.from(saved, items);
        writeAudit(
                auditContext,
                AdminAuditAction.INBOUND_ORDER_CANCEL,
                saved.getInboundNo(),
                before,
                after,
                "Cancel inbound order draft " + saved.getInboundNo() + " with " + itemSummary(items));
        return after;
    }

    @Transactional
    public InboundOrderResponse confirm(String inboundNo, InventoryAdminAuditContext auditContext) {
        String requestId = requireRequestId(auditContext.requestId());
        InboundOrder priorByRequestId = inboundOrderRepository.findByConfirmRequestId(requestId).orElse(null);
        if (priorByRequestId != null) {
            if (!priorByRequestId.getInboundNo().equals(inboundNo)) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "requestId already used for another inbound order");
            }
            return response(priorByRequestId);
        }

        InboundOrder order = getByInboundNo(inboundNo);
        if (order.getStatus() == InboundOrderStatus.CONFIRMED
                || order.getStatus() == InboundOrderStatus.APPLIED) {
            return response(order);
        }
        if (order.getStatus() != InboundOrderStatus.DRAFT) {
            throw new BusinessException(ErrorCode.CONFLICT, "Only draft inbound orders can be confirmed");
        }

        List<InboundOrderItem> items = inboundOrderItemRepository.findByInboundNoOrderByIdAsc(order.getInboundNo());
        InboundOrderResponse before = InboundOrderResponse.from(order, items);
        order.confirm(requestId, auditContext.adminUserId(), auditContext.adminUsername());
        applyInboundInventory(items);
        writeInboundInventoryRecords(order, items, requestId);
        order.apply();
        InboundOrder saved = inboundOrderRepository.saveAndFlush(order);
        InboundOrderResponse after = InboundOrderResponse.from(saved, items);
        writeAudit(
                auditContext,
                AdminAuditAction.INBOUND_ORDER_CONFIRM,
                saved.getInboundNo(),
                before,
                after,
                "Confirm inbound order " + saved.getInboundNo() + " with " + itemSummary(items));
        return after;
    }

    private InboundOrder getByInboundNo(String inboundNo) {
        return inboundOrderRepository.findByInboundNo(inboundNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Inbound order not found"));
    }

    private InboundOrderResponse response(InboundOrder order) {
        return InboundOrderResponse.from(
                order, inboundOrderItemRepository.findByInboundNoOrderByIdAsc(order.getInboundNo()));
    }

    private void applyInboundInventory(List<InboundOrderItem> items) {
        for (InboundOrderItem item : items) {
            Inventory inventory = inventoryRepository.findByProductId(item.getProductId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Inventory not found"));
            inventory.adjustAvailableStock(item.getQuantity());
        }
        inventoryRepository.flush();
    }

    private void writeInboundInventoryRecords(InboundOrder order, List<InboundOrderItem> items, String requestId) {
        inventoryRecordRepository.saveAllAndFlush(items.stream()
                .map(item -> new InventoryRecord(
                        item.getProductId(),
                        null,
                        InventoryChangeType.ADJUST_INCREASE,
                        item.getQuantity(),
                        requestId,
                        "Confirm inbound order " + order.getInboundNo(),
                        order.getConfirmedByAdminUserId(),
                        order.getConfirmedByAdminUsername(),
                        InventoryRecordSourceType.INBOUND_ORDER,
                        order.getInboundNo()))
                .toList());
    }

    private String requireRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "X-Request-Id is required for inbound confirmation");
        }
        return requestId.trim();
    }

    private List<CreateInboundOrderDraftItemRequest> normalizedItems(CreateInboundOrderDraftRequest request) {
        List<CreateInboundOrderDraftItemRequest> items = new ArrayList<>(request.items());
        Set<String> productIds = new HashSet<>();
        for (CreateInboundOrderDraftItemRequest item : items) {
            String productId = item.productId().trim();
            if (!productIds.add(productId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Duplicate productId in inbound draft");
            }
        }
        return items;
    }

    private Map<String, List<InboundOrderItem>> itemsByInboundNo(List<InboundOrder> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        List<String> inboundNos = orders.stream()
                .map(InboundOrder::getInboundNo)
                .toList();
        return inboundOrderItemRepository.findByInboundNoInOrderByInboundNoAscIdAsc(inboundNos).stream()
                .collect(Collectors.groupingBy(InboundOrderItem::getInboundNo));
    }

    private void writeAudit(
            InventoryAdminAuditContext auditContext,
            AdminAuditAction action,
            String inboundNo,
            InboundOrderResponse before,
            InboundOrderResponse after,
            String summary) {
        adminAuditWriter.write(new AdminAuditLogWriteRequest(
                auditContext.adminUserId(),
                auditContext.adminUsername(),
                action,
                AdminAuditResourceType.INBOUND_ORDER,
                inboundNo,
                auditContext.requestId(),
                null,
                inboundNo,
                toSnapshot(before),
                toSnapshot(after),
                auditContext.ip(),
                auditContext.userAgent(),
                summary));
    }

    private JsonNode toSnapshot(InboundOrderResponse response) {
        return response == null ? null : objectMapper.valueToTree(response);
    }

    private String itemSummary(List<InboundOrderItem> items) {
        String firstItems = items.stream()
                .limit(SUMMARY_ITEM_LIMIT)
                .map(item -> item.getProductId() + " x" + item.getQuantity())
                .collect(Collectors.joining(", "));
        int remaining = items.size() - Math.min(items.size(), SUMMARY_ITEM_LIMIT);
        String suffix = remaining > 0 ? ", +" + remaining + " more" : "";
        int totalQuantity = items.stream()
                .mapToInt(InboundOrderItem::getQuantity)
                .sum();
        return items.size() + " item(s), totalQuantity=" + totalQuantity + ": " + firstItems + suffix;
    }

    private Pageable defaultSort(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private String generateInboundNo() {
        for (int attempt = 0; attempt < MAX_INBOUND_NO_GENERATION_ATTEMPTS; attempt++) {
            String inboundNo = "INB-" + UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, INBOUND_NO_RANDOM_LENGTH)
                    .toUpperCase(Locale.ROOT);
            if (!inboundOrderRepository.existsByInboundNo(inboundNo)) {
                return inboundNo;
            }
        }
        throw new BusinessException(ErrorCode.CONFLICT, "Unable to allocate inbound order number");
    }
}
