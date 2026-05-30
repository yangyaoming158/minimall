package com.minimall.inventory.web;

import com.minimall.common.auth.context.UserContext;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.ApiResponse;
import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.domain.StockState;
import com.minimall.inventory.dto.AdjustInventoryRequest;
import com.minimall.inventory.dto.AdminInventoryResponse;
import com.minimall.inventory.dto.InitializeInventoryRequest;
import com.minimall.inventory.dto.InventoryRecordResponse;
import com.minimall.inventory.service.InventoryCommandService;
import com.minimall.inventory.service.InventoryQueryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/inventories")
public class AdminInventoryController {

    private final InventoryQueryService inventoryQueryService;
    private final InventoryCommandService inventoryCommandService;

    public AdminInventoryController(
            InventoryQueryService inventoryQueryService,
            InventoryCommandService inventoryCommandService) {
        this.inventoryQueryService = inventoryQueryService;
        this.inventoryCommandService = inventoryCommandService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminInventoryResponse>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "stockState", required = false) String stockState,
            @RequestParam(name = "lowStock", required = false) Boolean lowStock,
            Pageable pageable) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(
                inventoryQueryService.adminList(keyword, parseStockState(stockState), lowStock, pageable));
    }

    @GetMapping("/{productId}")
    public ApiResponse<AdminInventoryResponse> detail(@PathVariable("productId") String productId) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(inventoryQueryService.adminDetail(productId));
    }

    @PostMapping
    public ApiResponse<AdminInventoryResponse> initialize(
            @Valid @RequestBody InitializeInventoryRequest request) {
        UserContext admin = AdminAccess.requireAdmin();
        return ApiResponse.success(
                inventoryCommandService.initialize(request, admin.getUserId(), admin.getUsername()));
    }

    @PostMapping("/{productId}/adjust")
    public ApiResponse<AdminInventoryResponse> adjust(
            @PathVariable("productId") String productId,
            @Valid @RequestBody AdjustInventoryRequest request) {
        UserContext admin = AdminAccess.requireAdmin();
        return ApiResponse.success(
                inventoryCommandService.adjust(productId, request, admin.getUserId(), admin.getUsername()));
    }

    @GetMapping("/{productId}/records")
    public ApiResponse<List<InventoryRecordResponse>> records(@PathVariable("productId") String productId) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(inventoryQueryService.records(productId));
    }

    private StockState parseStockState(String stockState) {
        if (!StringUtils.hasText(stockState)) {
            return null;
        }
        try {
            return StockState.valueOf(stockState.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid stockState", exception);
        }
    }
}
