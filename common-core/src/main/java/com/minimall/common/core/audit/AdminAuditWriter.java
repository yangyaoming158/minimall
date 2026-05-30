package com.minimall.common.core.audit;

public interface AdminAuditWriter {

    void write(AdminAuditLogWriteRequest request);
}
