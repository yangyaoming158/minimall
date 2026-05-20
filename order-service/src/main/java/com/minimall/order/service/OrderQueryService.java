package com.minimall.order.service;

import com.minimall.common.auth.context.UserContext;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.PageResponse;
import com.minimall.order.domain.Order;
import com.minimall.order.dto.OrderDetailResponse;
import com.minimall.order.dto.OrderItemSummary;
import com.minimall.order.dto.OrderSummaryResponse;
import com.minimall.order.repository.OrderRepository;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderQueryService {

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
}
