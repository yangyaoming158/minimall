package com.minimall.order.web;

import com.minimall.common.core.response.ApiResponse;
import com.minimall.common.core.response.PageResponse;
import com.minimall.order.dto.SalesByProductStatsResponse;
import com.minimall.order.service.OrderQueryService;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/operation-stats")
public class AdminOperationStatsController {

    private final OrderQueryService orderQueryService;

    public AdminOperationStatsController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    @GetMapping("/sales-by-product")
    public ApiResponse<PageResponse<SalesByProductStatsResponse>> salesByProduct(
            @RequestParam(name = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime createdFrom,
            @RequestParam(name = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime createdTo,
            Pageable pageable) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(orderQueryService.salesByProductStats(createdFrom, createdTo, pageable));
    }
}
