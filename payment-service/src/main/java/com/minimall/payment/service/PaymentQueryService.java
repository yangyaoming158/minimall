package com.minimall.payment.service;

import com.minimall.common.auth.context.UserContext;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.PageResponse;
import com.minimall.payment.domain.Order;
import com.minimall.payment.domain.Payment;
import com.minimall.payment.domain.PaymentStatus;
import com.minimall.payment.dto.AdminPaymentResponse;
import com.minimall.payment.dto.PaymentResponse;
import com.minimall.payment.repository.OrderRepository;
import com.minimall.payment.repository.PaymentRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PaymentQueryService {

    private static final String PAYMENT_NOT_FOUND_MESSAGE = "Payment not found";
    private static final int MAX_ADMIN_PAYMENT_PAGE_SIZE = 100;

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

    @Transactional(readOnly = true)
    public AdminPaymentResponse adminDetailByPaymentNo(String paymentNo) {
        Payment payment = paymentRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, PAYMENT_NOT_FOUND_MESSAGE));
        return AdminPaymentResponse.from(payment, orderRepository.findByOrderNo(payment.getOrderNo()).orElse(null));
    }

    @Transactional(readOnly = true)
    public AdminPaymentResponse adminDetailByOrderNo(String orderNo) {
        Payment payment = paymentRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, PAYMENT_NOT_FOUND_MESSAGE));
        return AdminPaymentResponse.from(payment, orderRepository.findByOrderNo(orderNo).orElse(null));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentResponse> adminList(
            String paymentNo,
            String orderNo,
            PaymentStatus status,
            LocalDateTime paidFrom,
            LocalDateTime paidTo,
            Pageable pageable) {
        if (paidFrom != null && paidTo != null && paidFrom.isAfter(paidTo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "paidFrom must be before or equal to paidTo");
        }
        Specification<Payment> specification = adminPaymentSpecification(paymentNo, orderNo, status, paidFrom, paidTo);
        Page<Payment> page = paymentRepository.findAll(specification, boundedAdminPaymentPageable(pageable));

        Map<String, Order> ordersByOrderNo = loadOrders(page.getContent());
        return PageResponse.from(page.map(payment ->
                AdminPaymentResponse.from(payment, ordersByOrderNo.get(payment.getOrderNo()))));
    }

    private Map<String, Order> loadOrders(List<Payment> payments) {
        List<String> orderNos = payments.stream().map(Payment::getOrderNo).distinct().toList();
        if (orderNos.isEmpty()) {
            return Map.of();
        }
        return orderRepository.findByOrderNoIn(orderNos).stream()
                .collect(Collectors.toMap(Order::getOrderNo, Function.identity()));
    }

    private Pageable boundedAdminPaymentPageable(Pageable pageable) {
        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.max(1, Math.min(pageable.getPageSize(), MAX_ADMIN_PAYMENT_PAGE_SIZE));
        // Stable, deterministic newest-first ordering regardless of any client-supplied sort.
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private Specification<Payment> adminPaymentSpecification(
            String paymentNo,
            String orderNo,
            PaymentStatus status,
            LocalDateTime paidFrom,
            LocalDateTime paidTo) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(paymentNo)) {
                predicates.add(criteriaBuilder.equal(root.get("paymentNo"), paymentNo.trim()));
            }
            if (StringUtils.hasText(orderNo)) {
                predicates.add(criteriaBuilder.equal(root.get("orderNo"), orderNo.trim()));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (paidFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("paidAt"), paidFrom));
            }
            if (paidTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("paidAt"), paidTo));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
