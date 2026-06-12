package com.minimall.inventory.web;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.ApiResponse;
import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.domain.AiOperationSuggestionStatus;
import com.minimall.inventory.dto.AiSuggestionResponse;
import com.minimall.inventory.dto.RejectAiSuggestionRequest;
import com.minimall.inventory.service.AiOperationSuggestionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
@RequestMapping("/api/admin/ai-suggestions")
public class AdminAiSuggestionController {

    private final AiOperationSuggestionService suggestionService;

    public AdminAiSuggestionController(AiOperationSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AiSuggestionResponse>> list(
            @RequestParam(name = "status", required = false) String status,
            Pageable pageable) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(suggestionService.list(parseStatus(status), pageable));
    }

    @GetMapping("/{suggestionNo}")
    public ApiResponse<AiSuggestionResponse> detail(@PathVariable("suggestionNo") String suggestionNo) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(suggestionService.detail(suggestionNo));
    }

    @PostMapping("/{suggestionNo}/reject")
    public ApiResponse<AiSuggestionResponse> reject(
            @PathVariable("suggestionNo") String suggestionNo,
            @Valid @RequestBody RejectAiSuggestionRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(suggestionService.reject(
                suggestionNo, request, AdminAccess.requireAdminAuditContext(servletRequest)));
    }

    @PostMapping("/{suggestionNo}/convert-inbound-draft")
    public ApiResponse<AiSuggestionResponse> convertToInboundDraft(
            @PathVariable("suggestionNo") String suggestionNo,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(suggestionService.convertToInboundDraft(
                suggestionNo, AdminAccess.requireAdminAuditContext(servletRequest)));
    }

    private AiOperationSuggestionStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return AiOperationSuggestionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid AI suggestion status", exception);
        }
    }
}
