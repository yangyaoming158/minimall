package com.minimall.inventory.web;

import com.minimall.common.core.response.ApiResponse;
import com.minimall.inventory.dto.AiDailyInventoryReportResponse;
import com.minimall.inventory.service.AiDailyInventoryReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai/reports")
public class AdminAiReportController {

    private final AiDailyInventoryReportService dailyInventoryReportService;

    public AdminAiReportController(AiDailyInventoryReportService dailyInventoryReportService) {
        this.dailyInventoryReportService = dailyInventoryReportService;
    }

    @GetMapping("/daily")
    public ApiResponse<AiDailyInventoryReportResponse> dailyReport() {
        AdminAccess.requireAdmin();
        return ApiResponse.success(dailyInventoryReportService.dailyReport());
    }
}
