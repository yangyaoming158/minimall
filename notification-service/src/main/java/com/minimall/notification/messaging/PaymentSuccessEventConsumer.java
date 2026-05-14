package com.minimall.notification.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.event.payment.PaymentEventNames;
import com.minimall.common.core.event.payment.PaymentSuccessEvent;
import com.minimall.notification.domain.NotificationLog;
import com.minimall.notification.domain.NotificationType;
import com.minimall.notification.repository.NotificationLogRepository;
import java.time.LocalDateTime;
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
    private static final String RESULT_SENT = "sent";
    private static final String RESULT_FAILED = "failed";

    private final NotificationLogRepository notificationLogRepository;
    private final ObjectMapper objectMapper;

    public PaymentSuccessEventConsumer(
            NotificationLogRepository notificationLogRepository,
            ObjectMapper objectMapper) {
        this.notificationLogRepository = notificationLogRepository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = PaymentEventNames.NOTIFICATION_PAYMENT_SUCCESS_QUEUE)
    @Transactional
    public void handle(PaymentSuccessEvent event) {
        try {
            saveNotificationLog(event);
        } catch (RuntimeException exception) {
            log.error("Payment success notification handling failed: eventId={}", event.getEventId(), exception);
            recordFailure(event, exception);
        }
    }

    private void saveNotificationLog(PaymentSuccessEvent event) {
        if (notificationLogRepository.existsByEventId(event.getEventId())) {
            log.info("Duplicate payment success notification event ignored: eventId={}", event.getEventId());
            return;
        }

        NotificationLog notificationLog = new NotificationLog(
                event.getEventId(),
                NotificationType.PAYMENT_SUCCESS,
                event.getOrderNo(),
                payload(event, RESULT_SENT, null));
        notificationLog.markSent(LocalDateTime.now());

        try {
            notificationLogRepository.saveAndFlush(notificationLog);
        } catch (DataIntegrityViolationException exception) {
            log.info(
                    "Duplicate payment success notification event ignored after unique constraint: eventId={}",
                    event.getEventId());
        }
    }

    private void recordFailure(PaymentSuccessEvent event, RuntimeException exception) {
        try {
            if (notificationLogRepository.existsByEventId(event.getEventId())) {
                return;
            }
            NotificationLog failedLog = new NotificationLog(
                    event.getEventId(),
                    NotificationType.PAYMENT_SUCCESS,
                    event.getOrderNo(),
                    payload(event, RESULT_FAILED, safeMessage(exception)));
            failedLog.markFailed(safeMessage(exception));
            notificationLogRepository.saveAndFlush(failedLog);
        } catch (RuntimeException failureException) {
            log.error("Failed to record notification failure: eventId={}", event.getEventId(), failureException);
        }
    }

    private String payload(PaymentSuccessEvent event, String handleResult, String errorMessage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.getEventId());
        payload.put("orderNo", event.getOrderNo());
        payload.put("paymentNo", event.getPaymentNo());
        payload.put("amount", event.getAmount());
        payload.put("paidAt", event.getPaidAt());
        payload.put("version", event.getVersion());
        payload.put("handleResult", handleResult);
        if (errorMessage != null) {
            payload.put("errorMessage", errorMessage);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return "{\"eventId\":\"" + event.getEventId() + "\",\"handleResult\":\"" + handleResult + "\"}";
        }
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }
}
