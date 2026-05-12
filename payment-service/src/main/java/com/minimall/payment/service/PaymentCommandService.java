package com.minimall.payment.service;

import com.minimall.common.auth.context.UserContext;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.payment.domain.Order;
import com.minimall.payment.domain.OrderStatus;
import com.minimall.payment.domain.Payment;
import com.minimall.payment.dto.PayPaymentRequest;
import com.minimall.payment.dto.PaymentResponse;
import com.minimall.payment.repository.OrderRepository;
import com.minimall.payment.repository.PaymentRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentCommandService {

    private static final DateTimeFormatter PAYMENT_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String ORDER_NOT_FOUND_MESSAGE = "Order not found";
    private static final String ORDER_NOT_PAYABLE_MESSAGE_PREFIX = "Order current status cannot be paid: ";

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    public PaymentCommandService(PaymentRepository paymentRepository, OrderRepository orderRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public PaymentResponse pay(String orderNo, PayPaymentRequest request, UserContext userContext) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .filter(found -> found.getUserId().equals(userContext.getUserId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, ORDER_NOT_FOUND_MESSAGE));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.CONFLICT, ORDER_NOT_PAYABLE_MESSAGE_PREFIX + order.getStatus());
        }

        Optional<Payment> existingPayment = paymentRepository.findByOrderNo(orderNo);
        if (existingPayment.isPresent()) {
            return PaymentResponse.from(markSuccessIfNecessary(existingPayment.get()));
        }

        try {
            Payment payment = new Payment(
                    nextPaymentNo(),
                    order.getOrderNo(),
                    order.getTotalAmount(),
                    request.normalizedChannel(),
                    request.idempotencyKey());
            payment.markSuccess(LocalDateTime.now());
            return PaymentResponse.from(paymentRepository.saveAndFlush(payment));
        } catch (DataIntegrityViolationException exception) {
            return paymentRepository.findByOrderNo(orderNo)
                    .map(this::markSuccessIfNecessary)
                    .map(PaymentResponse::from)
                    .orElseThrow(() -> exception);
        }
    }

    private Payment markSuccessIfNecessary(Payment payment) {
        if (payment.isSuccess()) {
            return payment;
        }
        payment.markSuccess(LocalDateTime.now());
        return paymentRepository.saveAndFlush(payment);
    }

    private String nextPaymentNo() {
        return "PAY" + LocalDateTime.now().format(PAYMENT_NO_TIME_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
