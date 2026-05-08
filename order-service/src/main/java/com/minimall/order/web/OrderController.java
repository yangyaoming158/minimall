package com.minimall.order.web;

import com.minimall.common.auth.context.UserContext;
import com.minimall.common.auth.context.UserContextHolder;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.ApiResponse;
import com.minimall.order.dto.CreateOrderRequest;
import com.minimall.order.dto.CreateOrderResponse;
import com.minimall.order.dto.OrderDetailResponse;
import com.minimall.order.dto.OrderSummaryResponse;
import com.minimall.order.dto.PageResponse;
import com.minimall.order.service.OrderCommandService;
import com.minimall.order.service.OrderQueryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderQueryService orderQueryService;
    private final OrderCommandService orderCommandService;

    public OrderController(OrderQueryService orderQueryService, OrderCommandService orderCommandService) {
        this.orderQueryService = orderQueryService;
        this.orderCommandService = orderCommandService;
    }

    @PostMapping
    public ApiResponse<CreateOrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success(orderCommandService.create(request, currentUser()));
    }

    @GetMapping("/my")
    public ApiResponse<PageResponse<OrderSummaryResponse>> myOrders(Pageable pageable) {
        return ApiResponse.success(orderQueryService.myOrders(currentUser(), pageable));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderDetailResponse> detail(@PathVariable("orderNo") String orderNo) {
        return ApiResponse.success(orderQueryService.detail(orderNo, currentUser()));
    }

    private UserContext currentUser() {
        return UserContextHolder.get()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Unauthorized"));
    }
}
