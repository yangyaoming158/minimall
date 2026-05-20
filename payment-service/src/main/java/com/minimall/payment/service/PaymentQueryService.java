package com.minimall.payment.service;

import com.minimall.common.auth.context.UserContext;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.payment.domain.Order;
import com.minimall.payment.domain.Payment;
import com.minimall.payment.dto.PaymentResponse;
import com.minimall.payment.repository.OrderRepository;
import com.minimall.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentQueryService {

    private static final String PAYMENT_NOT_FOUND_MESSAGE = "Payment not found";

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    public PaymentQueryService(PaymentRepository paymentRepository, OrderRepository orderRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public PaymentResponse detailByOrderNo(String orderNo, UserContext userContext) {
        Payment payment = paymentRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, PAYMENT_NOT_FOUND_MESSAGE));
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, PAYMENT_NOT_FOUND_MESSAGE));
        if (!order.getUserId().equals(userContext.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, PAYMENT_NOT_FOUND_MESSAGE);
        }
        return PaymentResponse.from(payment, order);
    }
}
