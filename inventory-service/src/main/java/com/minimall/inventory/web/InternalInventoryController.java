package com.minimall.inventory.web;

import com.minimall.common.core.response.ApiResponse;
import com.minimall.inventory.dto.InventoryChangeRequest;
import com.minimall.inventory.dto.InventoryResponse;
import com.minimall.inventory.service.InventoryCommandService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/inventories")
public class InternalInventoryController {

    private final InventoryCommandService inventoryCommandService;

    public InternalInventoryController(InventoryCommandService inventoryCommandService) {
        this.inventoryCommandService = inventoryCommandService;
    }

    @PostMapping("/deduct")
    public ApiResponse<InventoryResponse> deduct(@Valid @RequestBody InventoryChangeRequest request) {
        return ApiResponse.success(inventoryCommandService.deduct(request));
    }

    @PostMapping("/release")
    public ApiResponse<InventoryResponse> release(@Valid @RequestBody InventoryChangeRequest request) {
        return ApiResponse.success(inventoryCommandService.release(request));
    }
}