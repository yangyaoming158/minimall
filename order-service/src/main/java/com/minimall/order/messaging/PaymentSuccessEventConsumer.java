package com.minimall.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.event.payment.PaymentEventNames;
import com.minimall.common.core.event.payment.PaymentSuccessEvent;
import com.minimall.order.domain.Order;
import com.minimall.order.domain.OrderEvent;
import com.minimall.order.domain.OrderEventType;
import com.minimall.order.domain.OrderStateMachine;
import com.minimall.order.domain.OrderStatus;
import com.minimall.order.repository.OrderEventRepository;
import com.minimall.order.repository.OrderRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentSuccessEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentSuccessEventConsumer.class);
    private static final String RESULT_PROCESSED = "processed";
    private static final String RESULT_IGNORED = "ignored";
    private static final String RESULT_FAILED = "failed";

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final ObjectMapper objectMapper;
    private final OrderStateMachine orderStateMachine = new OrderStateMachine();

    public PaymentSuccessEventConsumer(
            OrderRepository orderRepository,
            OrderEventRepository orderEventRepository,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.orderEventRepository = orderEventRepository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = PaymentEventNames.ORDER_PAYMENT_SUCCESS_QUEUE)
    @Transactional
    public void handle(PaymentSuccessEvent event) {
        if (orderEventRepository.existsByEventId(event.getEventId())) {
            log.info("Duplicate payment success event ignored: eventId={}", event.getEventId());
            return;
        }

        OrderEvent orderEvent = new OrderEvent(
                event.getEventId(),
                event.getOrderNo(),
                OrderEventType.PAYMENT_SUCCESS,
                null,
                null,
                null);
        try {
            orderEventRepository.saveAndFlush(orderEvent);
        } catch (DataIntegrityViolationException exception) {
            log.info("Duplicate payment success event ignored after unique constraint: eventId={}", event.getEventId());
            return;
        }

        orderRepository.findByOrderNo(event.getOrderNo())
                .ifPresentOrElse(
                        order -> handleExistingOrder(event, orderEvent, order),
                        () -> orderEvent.updateResult(
                                null,
                                null,
                                payload(event, RESULT_FAILED, null, "Order not found")));
    }

    private void handleExistingOrder(PaymentSuccessEvent event, OrderEvent orderEvent, Order order) {
        OrderStatus fromStatus = order.getStatus();
        if (fromStatus == OrderStatus.PENDING_PAYMENT) {
            LocalDateTime paidAt = LocalDateTime.ofInstant(event.getPaidAt(), ZoneId.systemDefault());
            orderStateMachine.transition(order, OrderStatus.PAID, paidAt);
            orderRepository.saveAndFlush(order);
            orderEvent.updateResult(
                    fromStatus,
                    OrderStatus.PAID,
                    payload(event, RESULT_PROCESSED, null, null));
            return;
        }

        orderEvent.updateResult(
                fromStatus,
                fromStatus,
                payload(event, RESULT_IGNORED, "Order status is " + fromStatus, null));
    }

    private String payload(
            PaymentSuccessEvent event,
            String handleResult,
            String ignoredReason,
            String errorMessage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.getEventId());
        payload.put("orderNo", event.getOrderNo());
        payload.put("paymentNo", event.getPaymentNo());
        payload.put("amount", event.getAmount());
        payload.put("paidAt", event.getPaidAt());
        payload.put("version", event.getVersion());
        payload.put("handleResult", handleResult);
        if (ignoredReason != null) {
            payload.put("ignoredReason", ignoredReason);
        }
        if (errorMessage != null) {
            payload.put("errorMessage", errorMessage);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return "{\"handleResult\":\"" + handleResult + "\"}";
        }
    }
}
