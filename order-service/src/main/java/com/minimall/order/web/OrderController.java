package com.minimall.order.web;

import com.minimall.common.auth.context.UserContext;
import com.minimall.common.auth.context.UserContextHolder;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.ApiResponse;
import com.minimall.order.dto.OrderDetailResponse;
import com.minimall.order.dto.OrderSummaryResponse;
import com.minimall.order.dto.PageResponse;
import com.minimall.order.service.OrderQueryService;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderQueryService orderQueryService;

    public OrderController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
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
