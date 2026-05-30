package com.minimall.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minimall.notification.domain.NotificationLog;
import com.minimall.notification.domain.NotificationLogStatus;
import com.minimall.notification.domain.NotificationType;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification_log_repository;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class NotificationLogRepositoryTest {

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesNotificationLogAndFindsByEventId() {
        NotificationLog saved = notificationLogRepository.saveAndFlush(new NotificationLog(
                "payment-event-1001",
                NotificationType.PAYMENT_SUCCESS,
                "ORD-1001",
                "{\"orderNo\":\"ORD-1001\",\"paymentNo\":\"PAY-1001\"}"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(NotificationLogStatus.PENDING);
        assertThat(saved.getSentAt()).isNull();
        assertThat(notificationLogRepository.findByEventId("payment-event-1001"))
                .isPresent()
                .get()
                .satisfies(log -> {
                    assertThat(log.getNotificationType()).isEqualTo(NotificationType.PAYMENT_SUCCESS);
                    assertThat(log.getRecipient()).isEqualTo("ORD-1001");
                    assertThat(log.getPayload()).contains("PAY-1001");
                });
        assertThat(notificationLogRepository.existsByEventId("payment-event-1001")).isTrue();
        assertThat(notificationLogRepository.existsByEventId("missing-event")).isFalse();
    }

    @Test
    void persistsEnumValuesAsStrings() {
        NotificationLog log = notificationLogRepository.saveAndFlush(new NotificationLog(
                "payment-event-1002",
                NotificationType.PAYMENT_SUCCESS,
                "ORD-1002",
                "{\"orderNo\":\"ORD-1002\"}"));

        log.markSent(LocalDateTime.now());
        notificationLogRepository.saveAndFlush(log);

        String notificationType = jdbcTemplate.queryForObject(
                "select notification_type from notification_logs where event_id = ?",
                String.class,
                "payment-event-1002");
        String status = jdbcTemplate.queryForObject(
                "select status from notification_logs where event_id = ?",
                String.class,
                "payment-event-1002");

        assertThat(notificationType).isEqualTo("PAYMENT_SUCCESS");
        assertThat(status).isEqualTo("SENT");
    }

    @Test
    void findAllBySpecificationFiltersByStatusAndCreatedRangeNewestFirst() {
        LocalDateTime base = LocalDateTime.of(2026, 5, 20, 0, 0);
        savePersisted("evt-spec-1001", "ORD-SPEC-A", NotificationLogStatus.SENT, base.plusDays(1));
        savePersisted("evt-spec-1002", "ORD-SPEC-B", NotificationLogStatus.SENT, base.plusDays(3));
        // Wrong status -> excluded.
        savePersisted("evt-spec-1003", "ORD-SPEC-C", NotificationLogStatus.FAILED, base.plusDays(2));
        // Out of range -> excluded.
        savePersisted("evt-spec-1004", "ORD-SPEC-D", NotificationLogStatus.SENT, base.plusDays(10));

        LocalDateTime from = base;
        LocalDateTime to = base.plusDays(5);
        Specification<NotificationLog> specification = (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), NotificationLogStatus.SENT),
                cb.greaterThanOrEqualTo(root.get("createdAt"), from),
                cb.lessThanOrEqualTo(root.get("createdAt"), to));

        Page<NotificationLog> page = notificationLogRepository.findAll(specification,
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(NotificationLog::getEventId)
                .containsExactly("evt-spec-1002", "evt-spec-1001");
    }

    @Test
    void rejectsDuplicateEventIdForIdempotency() {
        notificationLogRepository.saveAndFlush(new NotificationLog(
                "payment-event-duplicate",
                NotificationType.PAYMENT_SUCCESS,
                "ORD-1003",
                "{\"orderNo\":\"ORD-1003\"}"));

        NotificationLog duplicate = new NotificationLog(
                "payment-event-duplicate",
                NotificationType.PAYMENT_SUCCESS,
                "ORD-1003",
                "{\"orderNo\":\"ORD-1003\",\"duplicate\":true}");

        assertThatThrownBy(() -> notificationLogRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void savePersisted(String eventId, String orderNo, NotificationLogStatus status, LocalDateTime createdAt) {
        NotificationLog log = new NotificationLog(
                eventId, NotificationType.PAYMENT_SUCCESS, orderNo, "{\"orderNo\":\"" + orderNo + "\"}");
        if (status == NotificationLogStatus.SENT) {
            log.markSent(createdAt);
        } else if (status == NotificationLogStatus.FAILED) {
            log.markFailed("boom");
        }
        notificationLogRepository.saveAndFlush(log);
        jdbcTemplate.update(
                "update notification_logs set created_at = ?, updated_at = ? where event_id = ?",
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt),
                eventId);
    }
}
