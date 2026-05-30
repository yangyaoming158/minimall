package com.minimall.order.service;

import com.minimall.common.auth.context.UserContext;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.PageResponse;
import com.minimall.order.domain.Order;
import com.minimall.order.domain.OrderStatus;
import com.minimall.order.dto.OrderDetailResponse;
import com.minimall.order.dto.OrderItemSummary;
import com.minimall.order.dto.OrderSummaryResponse;
import com.minimall.order.dto.ProductSalesAggregationResponse;
import com.minimall.order.repository.ProductSalesAggregation;
import com.minimall.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderQueryService {

    private static final int MAX_PRODUCT_SALES_PAGE_SIZE = 100;

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse detail(String orderNo, UserContext userContext) {
        Order order = orderRepository.findByOrderNoAndUserId(orderNo, userContext.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Order not found"));
        return toDetailResponse(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> myOrders(UserContext userContext, Pageable pageable) {
        return PageResponse.from(orderRepository.findByUserId(userContext.getUserId(), pageable)
                .map(this::toSummaryResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSalesAggregationResponse> productSales(
            String productId,
            OrderStatus status,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            Pageable pageable) {
        validateCreatedRange(createdFrom, createdTo);
        Pageable boundedPageable = boundedPageable(pageable);
        return PageResponse.from(orderRepository.aggregateProductSales(
                        normalize(productId), status, createdFrom, createdTo, boundedPageable)
                .map(this::toProductSalesAggregationResponse));
    }

    private OrderDetailResponse toDetailResponse(Order order) {
        return new OrderDetailResponse(
                order.getOrderNo(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                items(order),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getExpireAt(),
                order.getPaidAt(),
                order.getClosedAt());
    }

    private OrderSummaryResponse toSummaryResponse(Order order) {
        return new OrderSummaryResponse(
                order.getOrderNo(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                items(order),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getExpireAt(),
                order.getPaidAt(),
                order.getClosedAt());
    }

    private List<OrderItemSummary> items(Order order) {
        return List.of(new OrderItemSummary(
                order.getProductId(),
                order.getProductName(),
                order.getQuantity(),
                order.getUnitPrice()));
    }

    private ProductSalesAggregationResponse toProductSalesAggregationResponse(ProductSalesAggregation aggregation) {
        return new ProductSalesAggregationResponse(
                aggregation.getProductId(),
                aggregation.getQuantitySold() == null ? 0L : aggregation.getQuantitySold(),
                aggregation.getOrderCount() == null ? 0L : aggregation.getOrderCount(),
                aggregation.getTotalAmount() == null ? BigDecimal.ZERO : aggregation.getTotalAmount());
    }

    private void validateCreatedRange(LocalDateTime createdFrom, LocalDateTime createdTo) {
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "createdFrom must be before or equal to createdTo");
        }
    }

    private Pageable boundedPageable(Pageable pageable) {
        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.max(1, Math.min(pageable.getPageSize(), MAX_PRODUCT_SALES_PAGE_SIZE));
        return PageRequest.of(page, size);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
