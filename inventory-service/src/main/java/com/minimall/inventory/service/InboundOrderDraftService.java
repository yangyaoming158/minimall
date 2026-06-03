package com.minimall.inventory.service;

import com.minimall.common.auth.context.UserContext;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.domain.InboundOrder;
import com.minimall.inventory.domain.InboundOrderItem;
import com.minimall.inventory.domain.InboundOrderStatus;
import com.minimall.inventory.dto.CreateInboundOrderDraftItemRequest;
import com.minimall.inventory.dto.CreateInboundOrderDraftRequest;
import com.minimall.inventory.dto.InboundOrderResponse;
import com.minimall.inventory.repository.InboundOrderItemRepository;
import com.minimall.inventory.repository.InboundOrderRepository;
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

    private final InboundOrderRepository inboundOrderRepository;
    private final InboundOrderItemRepository inboundOrderItemRepository;

    public InboundOrderDraftService(
            InboundOrderRepository inboundOrderRepository,
            InboundOrderItemRepository inboundOrderItemRepository) {
        this.inboundOrderRepository = inboundOrderRepository;
        this.inboundOrderItemRepository = inboundOrderItemRepository;
    }

    @Transactional
    public InboundOrderResponse createDraft(CreateInboundOrderDraftRequest request, UserContext admin) {
        List<CreateInboundOrderDraftItemRequest> items = normalizedItems(request);
        String inboundNo = generateInboundNo();
        InboundOrder order = inboundOrderRepository.saveAndFlush(
                new InboundOrder(inboundNo, admin.getUserId(), admin.getUsername()));

        List<InboundOrderItem> savedItems = inboundOrderItemRepository.saveAllAndFlush(items.stream()
                .map(item -> new InboundOrderItem(order.getInboundNo(), item.productId().trim(), item.quantity()))
                .toList());
        return InboundOrderResponse.from(order, savedItems);
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
    public InboundOrderResponse cancel(String inboundNo) {
        InboundOrder order = getByInboundNo(inboundNo);
        if (order.getStatus() == InboundOrderStatus.CANCELLED) {
            return detail(order.getInboundNo());
        }
        if (order.getStatus() != InboundOrderStatus.DRAFT) {
            throw new BusinessException(ErrorCode.CONFLICT, "Only draft inbound orders can be cancelled");
        }
        order.setStatus(InboundOrderStatus.CANCELLED);
        InboundOrder saved = inboundOrderRepository.saveAndFlush(order);
        return InboundOrderResponse.from(
                saved, inboundOrderItemRepository.findByInboundNoOrderByIdAsc(saved.getInboundNo()));
    }

    private InboundOrder getByInboundNo(String inboundNo) {
        return inboundOrderRepository.findByInboundNo(inboundNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Inbound order not found"));
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
