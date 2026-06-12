package com.minimall.notification.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minimall.common.auth.constants.AuthHeaders;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.notification.domain.NotificationLog;
import com.minimall.notification.domain.NotificationLogStatus;
import com.minimall.notification.domain.NotificationType;
import com.minimall.notification.repository.NotificationLogRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_notification_controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class AdminNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        notificationLogRepository.deleteAll();
    }

    @Test
    void adminNotificationListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void adminNotificationListRejectsUserRole() throws Exception {
        mockMvc.perform(get("/api/admin/notifications")
                        .header(AuthHeaders.USER_ID, "501")
                        .header(AuthHeaders.USERNAME, "operator")
                        .header(AuthHeaders.USER_ROLE, "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void adminNotificationListReturnsNewestFirstForAdmin() throws Exception {
        saveLog("evt-1001", "ORD-NOTIF-1001", NotificationLogStatus.SENT,
                LocalDateTime.of(2026, 5, 20, 10, 0));
        saveLog("evt-1002", "ORD-NOTIF-1002", NotificationLogStatus.FAILED,
                LocalDateTime.of(2026, 5, 22, 10, 0));
        saveLog("evt-1003", "ORD-NOTIF-1003", NotificationLogStatus.SENT,
                LocalDateTime.of(2026, 5, 21, 10, 0));

        mockMvc.perform(get("/api/admin/notifications")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].eventId").value("evt-1002"))
                .andExpect(jsonPath("$.data.content[0].orderNo").value("ORD-NOTIF-1002"))
                .andExpect(jsonPath("$.data.content[0].notificationType").value("PAYMENT_SUCCESS"))
                .andExpect(jsonPath("$.data.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.content[1].eventId").value("evt-1003"))
                .andExpect(jsonPath("$.data.content[2].eventId").value("evt-1001"));
    }

    @Test
    void adminNotificationListFiltersByEventIdOrderNoAndStatus() throws Exception {
        saveLog("evt-FILT-1001", "ORD-FILT-A", NotificationLogStatus.SENT,
                LocalDateTime.of(2026, 5, 20, 10, 0));
        saveLog("evt-FILT-1002", "ORD-FILT-B", NotificationLogStatus.FAILED,
                LocalDateTime.of(2026, 5, 20, 11, 0));

        mockMvc.perform(get("/api/admin/notifications")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].eventId").value("evt-FILT-1002"));

        mockMvc.perform(get("/api/admin/notifications")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("orderNo", "ORD-FILT-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].eventId").value("evt-FILT-1001"));

        mockMvc.perform(get("/api/admin/notifications")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("eventId", "evt-FILT-1002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].orderNo").value("ORD-FILT-B"));
    }

    @Test
    void adminNotificationListFiltersByChannelAndCreatedRange() throws Exception {
        saveLog("evt-RANGE-1001", "ORD-RANGE-A", NotificationLogStatus.SENT,
                LocalDateTime.of(2026, 5, 18, 10, 0));
        saveLog("evt-RANGE-1002", "ORD-RANGE-B", NotificationLogStatus.SENT,
                LocalDateTime.of(2026, 5, 25, 10, 0));

        mockMvc.perform(get("/api/admin/notifications")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("channel", "PAYMENT_SUCCESS")
                        .param("createdFrom", "2026-05-24T00:00:00")
                        .param("createdTo", "2026-05-26T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].eventId").value("evt-RANGE-1002"));
    }

    @Test
    void adminNotificationListRejectsInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/admin/notifications")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("Invalid status"));
    }

    @Test
    void adminNotificationListRejectsInvalidChannel() throws Exception {
        mockMvc.perform(get("/api/admin/notifications")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("channel", "EMAIL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("Invalid channel"));
    }

    @Test
    void adminNotificationListRejectsInvalidCreatedRange() throws Exception {
        mockMvc.perform(get("/api/admin/notifications")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("createdFrom", "2026-05-22T00:00:00")
                        .param("createdTo", "2026-05-21T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("createdFrom must be before or equal to createdTo"));
    }

    @Test
    void adminNotificationListBoundsPageSize() throws Exception {
        saveLog("evt-BOUND-1001", "ORD-BOUND-A", NotificationLogStatus.SENT,
                LocalDateTime.of(2026, 5, 20, 10, 0));

        mockMvc.perform(get("/api/admin/notifications")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void adminNotificationDetailReturnsForAdmin() throws Exception {
        NotificationLog saved = saveLog("evt-DET-1001", "ORD-DET-A", NotificationLogStatus.SENT,
                LocalDateTime.of(2026, 5, 20, 10, 0));

        mockMvc.perform(get("/api/admin/notifications/" + saved.getId())
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(saved.getId()))
                .andExpect(jsonPath("$.data.eventId").value("evt-DET-1001"))
                .andExpect(jsonPath("$.data.orderNo").value("ORD-DET-A"))
                .andExpect(jsonPath("$.data.notificationType").value("PAYMENT_SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.payload").exists())
                .andExpect(jsonPath("$.data.sentAt").exists());
    }

    @Test
    void adminNotificationDetailReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/admin/notifications/999999")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("Notification log not found"));
    }

    @Test
    void adminNotificationDetailRejectsUserRole() throws Exception {
        NotificationLog saved = saveLog("evt-DET-FORBID", "ORD-DET-FORBID", NotificationLogStatus.SENT,
                LocalDateTime.of(2026, 5, 20, 10, 0));

        mockMvc.perform(get("/api/admin/notifications/" + saved.getId())
                        .header(AuthHeaders.USER_ID, "642")
                        .header(AuthHeaders.USERNAME, "user")
                        .header(AuthHeaders.USER_ROLE, "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    private NotificationLog saveLog(
            String eventId, String orderNo, NotificationLogStatus status, LocalDateTime createdAt) {
        NotificationLog log = new NotificationLog(
                eventId, NotificationType.PAYMENT_SUCCESS, orderNo, "{\"orderNo\":\"" + orderNo + "\"}");
        if (status == NotificationLogStatus.SENT) {
            log.markSent(createdAt);
        } else if (status == NotificationLogStatus.FAILED) {
            log.markFailed("delivery failed");
        }
        NotificationLog saved = notificationLogRepository.saveAndFlush(log);
        jdbcTemplate.update(
                "update notification_logs set created_at = ?, updated_at = ? where event_id = ?",
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt),
                eventId);
        return saved;
    }
}
