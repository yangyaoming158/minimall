package com.minimall.common.core.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcAdminAuditWriter implements AdminAuditWriter {

    private static final String INSERT_SQL = """
            INSERT INTO admin_operation_logs (
              admin_user_id,
              admin_username,
              action,
              resource_type,
              resource_id,
              request_id,
              source_type,
              reference_no,
              before_snapshot,
              after_snapshot,
              ip,
              user_agent,
              summary
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAdminAuditWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public void write(AdminAuditLogWriteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        try {
            jdbcTemplate.update(
                    INSERT_SQL,
                    request.adminUserId(),
                    request.adminUsername(),
                    request.action().name(),
                    request.resourceType().name(),
                    request.resourceId(),
                    request.requestId(),
                    request.sourceType().name(),
                    request.referenceNo(),
                    jsonToString(request.beforeSnapshot()),
                    jsonToString(request.afterSnapshot()),
                    request.ip(),
                    request.userAgent(),
                    request.summary());
        } catch (DataAccessException exception) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to write admin audit log",
                    exception);
        }
    }

    private static String jsonToString(JsonNode jsonNode) {
        return jsonNode == null ? null : jsonNode.toString();
    }
}
