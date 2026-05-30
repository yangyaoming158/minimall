package com.minimall.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minimall.common.auth.context.AuthRole;
import com.minimall.common.auth.context.UserContextHolder;
import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.core.audit.AdminAuditAction;
import com.minimall.common.core.audit.AdminAuditResourceType;
import com.minimall.common.core.audit.AdminAuditSourceType;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.user.audit.AdminOperationLog;
import com.minimall.user.audit.AdminOperationLogRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_audit_log_controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "minimall.auth.jwt.secret=test-jwt-secret-for-admin-audit-log",
        "minimall.auth.jwt.expire-seconds=3600"
})
class AdminAuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminOperationLogRepository adminOperationLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        adminOperationLogRepository.deleteAll();
        UserContextHolder.clear();
    }

    @Test
    void listFiltersAuditLogsAndReturnsApiResponsePage() throws Exception {
        saveLog(
                1001L,
                "admin",
                AdminAuditAction.PRODUCT_CREATE,
                AdminAuditResourceType.PRODUCT,
                "SKU-1",
                "REQ-1",
                AdminAuditSourceType.ADMIN_MANUAL,
                null,
                null,
                "{\"name\":\"Keyboard\"}",
                "Create SKU-1",
                LocalDateTime.of(2026, 5, 30, 10, 0));
        saveLog(
                1002L,
                "ops",
                AdminAuditAction.PRODUCT_UPDATE,
                AdminAuditResourceType.PRODUCT,
                "SKU-1",
                "REQ-2",
                AdminAuditSourceType.ADMIN_MANUAL,
                null,
                "{\"name\":\"Keyboard\"}",
                "{\"name\":\"Mechanical Keyboard\"}",
                "Update SKU-1",
                LocalDateTime.of(2026, 5, 30, 11, 0));
        saveLog(
                1001L,
                "admin",
                AdminAuditAction.INVENTORY_ADJUST,
                AdminAuditResourceType.INVENTORY,
                "SKU-2",
                "REQ-3",
                AdminAuditSourceType.AI_SUGGESTION,
                "AIS-1",
                "{\"availableStock\":8}",
                "{\"availableStock\":13}",
                "Adjust SKU-2 stock by 5",
                LocalDateTime.of(2026, 5, 30, 12, 0));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("adminUserId", "1001")
                        .param("action", "INVENTORY_ADJUST")
                        .param("resourceType", "INVENTORY")
                        .param("resourceId", "SKU-2")
                        .param("requestId", "REQ-3")
                        .param("sourceType", "AI_SUGGESTION")
                        .param("referenceNo", "AIS-1")
                        .param("createdFrom", "2026-05-30T11:30:00Z")
                        .param("createdTo", "2026-05-30T12:30:00Z")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.content[0].adminUserId").value(1001))
                .andExpect(jsonPath("$.data.content[0].adminUsername").value("admin"))
                .andExpect(jsonPath("$.data.content[0].action").value("INVENTORY_ADJUST"))
                .andExpect(jsonPath("$.data.content[0].resourceType").value("INVENTORY"))
                .andExpect(jsonPath("$.data.content[0].resourceId").value("SKU-2"))
                .andExpect(jsonPath("$.data.content[0].requestId").value("REQ-3"))
                .andExpect(jsonPath("$.data.content[0].sourceType").value("AI_SUGGESTION"))
                .andExpect(jsonPath("$.data.content[0].referenceNo").value("AIS-1"))
                .andExpect(jsonPath("$.data.content[0].beforeSnapshot.availableStock").value(8))
                .andExpect(jsonPath("$.data.content[0].afterSnapshot.availableStock").value(13))
                .andExpect(jsonPath("$.data.content[0].summary").value("Adjust SKU-2 stock by 5"))
                .andExpect(jsonPath("$.data.content[0].createdAt").value("2026-05-30T12:00:00Z"));

        assertThat(UserContextHolder.hasContext()).isFalse();
    }

    @Test
    void listPaginatesByNewestAuditLogFirst() throws Exception {
        saveLog("oldest", LocalDateTime.of(2026, 5, 30, 10, 0));
        saveLog("middle", LocalDateTime.of(2026, 5, 30, 11, 0));
        saveLog("newest", LocalDateTime.of(2026, 5, 30, 12, 0));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].summary").value("newest"))
                .andExpect(jsonPath("$.data.content[1].summary").value("middle"));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.content[0].summary").value("oldest"));
    }

    @Test
    void listRejectsUserRole() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUtils.generateToken(
                                43L,
                                "alice",
                                AuthRole.USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.FORBIDDEN.getMessage()));

        assertThat(UserContextHolder.hasContext()).isFalse();
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        assertThat(UserContextHolder.hasContext()).isFalse();
    }

    @Test
    void listRejectsInvalidFilters() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("action", "not-an-action"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("Invalid action"));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("createdFrom", "not-an-instant"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("Invalid createdFrom"));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("size must be between 1 and 100"));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("createdFrom", "2026-05-30T12:00:00Z")
                        .param("createdTo", "2026-05-30T11:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("createdTo must not be before createdFrom"));
    }

    private String adminToken() {
        return jwtUtils.generateToken(42L, "admin", AuthRole.ADMIN);
    }

    private void saveLog(String summary, LocalDateTime createdAt) {
        saveLog(
                1001L,
                "admin",
                AdminAuditAction.PRODUCT_UPDATE,
                AdminAuditResourceType.PRODUCT,
                "SKU-1",
                null,
                AdminAuditSourceType.ADMIN_MANUAL,
                null,
                null,
                "{\"name\":\"" + summary + "\"}",
                summary,
                createdAt);
    }

    private AdminOperationLog saveLog(
            Long adminUserId,
            String adminUsername,
            AdminAuditAction action,
            AdminAuditResourceType resourceType,
            String resourceId,
            String requestId,
            AdminAuditSourceType sourceType,
            String referenceNo,
            String beforeSnapshot,
            String afterSnapshot,
            String summary,
            LocalDateTime createdAt) {
        AdminOperationLog saved = adminOperationLogRepository.saveAndFlush(new AdminOperationLog(
                adminUserId,
                adminUsername,
                action,
                resourceType,
                resourceId,
                requestId,
                sourceType,
                referenceNo,
                beforeSnapshot,
                afterSnapshot,
                "127.0.0.1",
                "MockMvc",
                summary));
        jdbcTemplate.update(
                "UPDATE admin_operation_logs SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(createdAt),
                saved.getId());
        return saved;
    }
}
