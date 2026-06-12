package com.minimall.payment.web;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.ApiResponse;
import com.minimall.common.core.response.PageResponse;
import com.minimall.payment.domain.PaymentStatus;
import com.minimall.payment.dto.AdminPaymentResponse;
import com.minimall.payment.service.PaymentQueryService;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {

    private final PaymentQueryService paymentQueryService;

    public AdminPaymentController(PaymentQueryService paymentQueryService) {
        this.paymentQueryService = paymentQueryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminPaymentResponse>> list(
            @RequestParam(name = "paymentNo", required = false) String paymentNo,
            @RequestParam(name = "orderNo", required = false) String orderNo,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "paidFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime paidFrom,
            @RequestParam(name = "paidTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime paidTo,
            Pageable pageable) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(paymentQueryService.adminList(
                paymentNo, orderNo, parseStatus(status), paidFrom, paidTo, pageable));
    }

    @GetMapping("/{paymentNo}")
    public ApiResponse<AdminPaymentResponse> detail(@PathVariable("paymentNo") String paymentNo) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(paymentQueryService.adminDetailByPaymentNo(paymentNo));
    }

    @GetMapping("/order/{orderNo}")
    public ApiResponse<AdminPaymentResponse> detailByOrderNo(@PathVariable("orderNo") String orderNo) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(paymentQueryService.adminDetailByOrderNo(orderNo));
    }

    private PaymentStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return PaymentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid status", exception);
        }
    }
}
