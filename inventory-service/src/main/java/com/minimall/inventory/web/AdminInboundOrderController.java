package com.minimall.inventory.web;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.ApiResponse;
import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.domain.InboundOrderStatus;
import com.minimall.inventory.dto.CreateInboundOrderDraftRequest;
import com.minimall.inventory.dto.InboundOrderResponse;
import com.minimall.inventory.service.InboundOrderDraftService;
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
@RequestMapping("/api/admin/inbound-orders")
public class AdminInboundOrderController {

    private final InboundOrderDraftService inboundOrderDraftService;

    public AdminInboundOrderController(InboundOrderDraftService inboundOrderDraftService) {
        this.inboundOrderDraftService = inboundOrderDraftService;
    }

    @PostMapping("/drafts")
    public ApiResponse<InboundOrderResponse> createDraft(
            @Valid @RequestBody CreateInboundOrderDraftRequest request) {
        return ApiResponse.success(inboundOrderDraftService.createDraft(request, AdminAccess.requireAdmin()));
    }

    @GetMapping
    public ApiResponse<PageResponse<InboundOrderResponse>> list(
            @RequestParam(name = "status", required = false) String status,
            Pageable pageable) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(inboundOrderDraftService.list(parseStatus(status), pageable));
    }

    @GetMapping("/{inboundNo}")
    public ApiResponse<InboundOrderResponse> detail(@PathVariable("inboundNo") String inboundNo) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(inboundOrderDraftService.detail(inboundNo));
    }

    @PostMapping("/{inboundNo}/cancel")
    public ApiResponse<InboundOrderResponse> cancel(@PathVariable("inboundNo") String inboundNo) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(inboundOrderDraftService.cancel(inboundNo));
    }

    private InboundOrderStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return InboundOrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid inbound order status", exception);
        }
    }
}
