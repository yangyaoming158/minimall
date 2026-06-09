package com.minimall.inventory.web;

import com.minimall.common.core.response.ApiResponse;
import com.minimall.inventory.dto.AiInventoryAnalysisResponse;
import com.minimall.inventory.dto.AiInventoryLowStockAnalysisRequest;
import com.minimall.inventory.service.AiInventoryAnalysisApiService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai/inventory")
public class AdminAiInventoryAnalysisController {

    private final AiInventoryAnalysisApiService analysisApiService;

    public AdminAiInventoryAnalysisController(AiInventoryAnalysisApiService analysisApiService) {
        this.analysisApiService = analysisApiService;
    }

    @PostMapping("/low-stock-analysis")
    public ApiResponse<AiInventoryAnalysisResponse> lowStockAnalysis(
            @Valid @RequestBody(required = false) AiInventoryLowStockAnalysisRequest request) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(analysisApiService.lowStockAnalysis(request));
    }
}
