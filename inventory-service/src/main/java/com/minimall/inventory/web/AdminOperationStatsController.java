package com.minimall.inventory.web;

import com.minimall.common.core.response.ApiResponse;
import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.dto.InventoryTrendResponse;
import com.minimall.inventory.service.InventoryQueryService;
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

    private final InventoryQueryService inventoryQueryService;

    public AdminOperationStatsController(InventoryQueryService inventoryQueryService) {
        this.inventoryQueryService = inventoryQueryService;
    }

    @GetMapping("/inventory-trends")
    public ApiResponse<PageResponse<InventoryTrendResponse>> inventoryTrends(
            @RequestParam(name = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime createdFrom,
            @RequestParam(name = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime createdTo,
            Pageable pageable) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(inventoryQueryService.inventoryTrends(createdFrom, createdTo, pageable));
    }
}
