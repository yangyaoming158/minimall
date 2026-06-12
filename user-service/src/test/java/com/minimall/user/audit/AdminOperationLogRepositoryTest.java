package com.minimall.user.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.audit.AdminAuditAction;
import com.minimall.common.core.audit.AdminAuditLogWriteRequest;
import com.minimall.common.core.audit.AdminAuditResourceType;
import com.minimall.common.core.audit.AdminAuditSourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class AdminOperationLogRepositoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private AdminOperationLogRepository adminOperationLogRepository;

    @Test
    void persistsAuditLogWithSharedEnumsAndSnapshots() throws Exception {
        AdminOperationLog saved = adminOperationLogRepository.saveAndFlush(AdminOperationLog.from(
                new AdminAuditLogWriteRequest(
                        1001L,
                        "admin",
                        AdminAuditAction.INVENTORY_ADJUST,
                        AdminAuditResourceType.INVENTORY,
                        "SKU-1",
                        "REQ-1",
                        AdminAuditSourceType.AI_SUGGESTION,
                        "AIS-1",
                        objectMapper.readTree("{\"availableStock\":8}"),
                        objectMapper.readTree("{\"availableStock\":13}"),
                        "127.0.0.1",
                        "Mozilla/5.0",
                        "Adjust SKU-1 stock by 5")));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getAction()).isEqualTo(AdminAuditAction.INVENTORY_ADJUST);
        assertThat(saved.getResourceType()).isEqualTo(AdminAuditResourceType.INVENTORY);
        assertThat(saved.getSourceType()).isEqualTo(AdminAuditSourceType.AI_SUGGESTION);
        assertThat(saved.getBeforeSnapshot()).contains("\"availableStock\":8");
        assertThat(saved.getAfterSnapshot()).contains("\"availableStock\":13");

        assertThat(adminOperationLogRepository.findByRequestIdOrderByCreatedAtDesc("REQ-1"))
                .singleElement()
                .extracting(AdminOperationLog::getReferenceNo)
                .isEqualTo("AIS-1");
    }

    @Test
    void defaultsSourceTypeAndNormalizesBlankOptionalFields() {
        AdminOperationLog saved = adminOperationLogRepository.saveAndFlush(new AdminOperationLog(
                1001L,
                "admin",
                AdminAuditAction.PRODUCT_CREATE,
                AdminAuditResourceType.PRODUCT,
                " ",
                "",
                null,
                " ",
                "",
                null,
                " ",
                "",
                "Create product"));

        assertThat(saved.getSourceType()).isEqualTo(AdminAuditSourceType.ADMIN_MANUAL);
        assertThat(saved.getResourceId()).isNull();
        assertThat(saved.getRequestId()).isNull();
        assertThat(saved.getReferenceNo()).isNull();
        assertThat(saved.getBeforeSnapshot()).isNull();
        assertThat(saved.getIp()).isNull();
        assertThat(saved.getUserAgent()).isNull();
    }

    @Test
    void supportsResourceTimelineLookupAndPaging() {
        AdminOperationLog first = new AdminOperationLog(
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
                null,
                null,
                "Create SKU-1");
        AdminOperationLog second = new AdminOperationLog(
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
                null,
                null,
                "Update SKU-1");
        adminOperationLogRepository.saveAndFlush(first);
        adminOperationLogRepository.saveAndFlush(second);

        assertThat(adminOperationLogRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
                AdminAuditResourceType.PRODUCT,
                "SKU-1"))
                .hasSize(2)
                .extracting(AdminOperationLog::getResourceId)
                .containsOnly("SKU-1");
        assertThat(adminOperationLogRepository.findAll(PageRequest.of(0, 1)).getTotalElements()).isEqualTo(2);
    }
}
