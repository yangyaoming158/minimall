package com.minimall.payment.web;

import com.minimall.common.auth.context.UserContext;
import com.minimall.common.auth.context.UserContextHolder;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.ApiResponse;
import com.minimall.payment.dto.PayPaymentRequest;
import com.minimall.payment.dto.PaymentResponse;
import com.minimall.payment.service.PaymentCommandService;
import com.minimall.payment.service.PaymentQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentCommandService paymentCommandService;
    private final PaymentQueryService paymentQueryService;

    public PaymentController(PaymentCommandService paymentCommandService, PaymentQueryService paymentQueryService) {
        this.paymentCommandService = paymentCommandService;
        this.paymentQueryService = paymentQueryService;
    }

    @PostMapping("/{orderNo}/pay")
    public ApiResponse<PaymentResponse> pay(
            @PathVariable("orderNo") String orderNo,
            @Valid @RequestBody(required = false) PayPaymentRequest request) {
        PayPaymentRequest normalizedRequest = request == null ? new PayPaymentRequest(null, null) : request;
        return ApiResponse.success(paymentCommandService.pay(orderNo, normalizedRequest, currentUser()));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<PaymentResponse> detail(@PathVariable("orderNo") String orderNo) {
        return ApiResponse.success(paymentQueryService.detailByOrderNo(orderNo, currentUser()));
    }

    private UserContext currentUser() {
        return UserContextHolder.get()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Unauthorized"));
    }
}
