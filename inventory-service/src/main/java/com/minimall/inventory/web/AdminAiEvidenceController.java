package com.minimall.inventory.web;

import com.minimall.common.core.response.ApiResponse;
import com.minimall.inventory.dto.AiInventoryEvidenceResponse;
import com.minimall.inventory.dto.AiInventorySalesEvidenceResponse;
import com.minimall.inventory.service.AiInventoryEvidenceFacade;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai/inventory/evidence")
public class AdminAiEvidenceController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int DEFAULT_RECORD_LIMIT = 5;
    private static final int DEFAULT_HOT_PRODUCT_DAYS = 7;

    private final AiInventoryEvidenceFacade evidenceFacade;

    public AdminAiEvidenceController(AiInventoryEvidenceFacade evidenceFacade) {
        this.evidenceFacade = evidenceFacade;
    }

    @GetMapping("/current/{productId}")
    public ApiResponse<AiInventoryEvidenceResponse> currentInventory(
            @PathVariable("productId") String productId,
            @RequestParam(name = "recordLimit", defaultValue = "" + DEFAULT_RECORD_LIMIT) int recordLimit) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(evidenceFacade.currentInventory(productId, recordLimit));
    }

    @GetMapping("/low-stock-candidates")
    public ApiResponse<AiInventoryEvidenceResponse> lowStockCandidates(
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit,
            @RequestParam(name = "recordLimit", defaultValue = "" + DEFAULT_RECORD_LIMIT) int recordLimit) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(evidenceFacade.lowStockCandidates(limit, recordLimit));
    }

    @GetMapping("/low-stock-analysis")
    public ApiResponse<AiInventorySalesEvidenceResponse> lowStockAnalysisEvidence(
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit,
            @RequestParam(name = "recordLimit", defaultValue = "" + DEFAULT_RECORD_LIMIT) int recordLimit) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(evidenceFacade.lowStockAnalysisEvidence(limit, recordLimit));
    }

    @GetMapping("/hot-products")
    public ApiResponse<AiInventorySalesEvidenceResponse> hotProductsEvidence(
            @RequestParam(name = "days", defaultValue = "" + DEFAULT_HOT_PRODUCT_DAYS) int days,
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit,
            @RequestParam(name = "recordLimit", defaultValue = "" + DEFAULT_RECORD_LIMIT) int recordLimit) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(evidenceFacade.hotProductsEvidence(days, limit, recordLimit));
    }
}
