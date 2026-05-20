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
import com.minimall.payment.service.event.PaymentEventPublisher;
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
    private static final String ORDER_CANCELLED_MESSAGE = "Order has been cancelled";
    private static final String ORDER_INVALID_STATE_MESSAGE_PREFIX = "Order current status cannot be paid: ";
    private static final String PAYMENT_ALREADY_SUCCESS_MESSAGE = "Payment already successful";

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentEventPublisher paymentEventPublisher;

    public PaymentCommandService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            PaymentEventPublisher paymentEventPublisher) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.paymentEventPublisher = paymentEventPublisher;
    }

    @Transactional
    public PaymentResponse pay(String orderNo, PayPaymentRequest request, UserContext userContext) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .filter(found -> found.getUserId().equals(userContext.getUserId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, ORDER_NOT_FOUND_MESSAGE));
        validatePayable(order);

        Optional<Payment> existingPayment = paymentRepository.findByOrderNo(orderNo);
        if (existingPayment.isPresent()) {
            return PaymentResponse.from(completeExistingPayment(existingPayment.get()), order);
        }

        try {
            Payment payment = new Payment(
                    nextPaymentNo(),
                    order.getOrderNo(),
                    order.getTotalAmount(),
                    request.normalizedChannel(),
                    request.idempotencyKey());
            payment.markSuccess(LocalDateTime.now());
            Payment saved = paymentRepository.saveAndFlush(payment);
            paymentEventPublisher.publishSuccess(saved);
            return PaymentResponse.from(saved, order);
        } catch (DataIntegrityViolationException exception) {
            return paymentRepository.findByOrderNo(orderNo)
                    .map(this::completeExistingPayment)
                    .map(payment -> PaymentResponse.from(payment, order))
                    .orElseThrow(() -> exception);
        }
    }

    private void validatePayable(Order order) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ORDER_CANCELLED, ORDER_CANCELLED_MESSAGE);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(
                    ErrorCode.ORDER_INVALID_STATE,
                    ORDER_INVALID_STATE_MESSAGE_PREFIX + order.getStatus());
        }
    }

    private Payment completeExistingPayment(Payment payment) {
        if (payment.isSuccess()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_SUCCESS, PAYMENT_ALREADY_SUCCESS_MESSAGE);
        }
        payment.markSuccess(LocalDateTime.now());
        Payment saved = paymentRepository.saveAndFlush(payment);
        paymentEventPublisher.publishSuccess(saved);
        return saved;
    }

    private String nextPaymentNo() {
        return "PAY" + LocalDateTime.now().format(PAYMENT_NO_TIME_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
