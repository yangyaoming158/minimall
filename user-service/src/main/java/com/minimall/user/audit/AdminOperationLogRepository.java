package com.minimall.user.audit;

import com.minimall.common.core.audit.AdminAuditResourceType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AdminOperationLogRepository
        extends JpaRepository<AdminOperationLog, Long>, JpaSpecificationExecutor<AdminOperationLog> {

    List<AdminOperationLog> findByRequestIdOrderByCreatedAtDesc(String requestId);

    List<AdminOperationLog> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
            AdminAuditResourceType resourceType,
            String resourceId);
}
