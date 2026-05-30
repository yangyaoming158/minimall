package com.minimall.order.web;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.ApiResponse;
import com.minimall.common.core.response.PageResponse;
import com.minimall.order.domain.OrderStatus;
import com.minimall.order.dto.ProductSalesAggregationResponse;
import com.minimall.order.service.OrderQueryService;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderQueryService orderQueryService;

    public AdminOrderController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    @GetMapping("/product-sales")
    public ApiResponse<PageResponse<ProductSalesAggregationResponse>> productSales(
            @RequestParam(name = "productId", required = false) String productId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime createdFrom,
            @RequestParam(name = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime createdTo,
            Pageable pageable) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(orderQueryService.productSales(
                productId, parseStatus(status), createdFrom, createdTo, pageable));
    }

    private OrderStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid status", exception);
        }
    }
}
