package com.minimall.inventory.web;

import com.minimall.common.core.response.ApiResponse;
import com.minimall.inventory.dto.AiReplenishmentSuggestionGenerateRequest;
import com.minimall.inventory.dto.AiSuggestionResponse;
import com.minimall.inventory.service.AiReplenishmentSuggestionService;
import com.minimall.inventory.service.InventoryAdminAuditContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai/replenishment-suggestions")
public class AdminAiReplenishmentSuggestionController {

    private final AiReplenishmentSuggestionService replenishmentSuggestionService;

    public AdminAiReplenishmentSuggestionController(
            AiReplenishmentSuggestionService replenishmentSuggestionService) {
        this.replenishmentSuggestionService = replenishmentSuggestionService;
    }

    @PostMapping("/generate")
    public ApiResponse<AiSuggestionResponse> generate(
            @Valid @RequestBody(required = false) AiReplenishmentSuggestionGenerateRequest request,
            HttpServletRequest httpRequest) {
        InventoryAdminAuditContext auditContext = AdminAccess.requireAdminAuditContext(httpRequest);
        AiReplenishmentSuggestionGenerateRequest normalized = request == null
                ? new AiReplenishmentSuggestionGenerateRequest(null, null)
                : request;
        return ApiResponse.success(replenishmentSuggestionService.generate(
                normalized.limit(), normalized.recordLimit(), auditContext));
    }
}
