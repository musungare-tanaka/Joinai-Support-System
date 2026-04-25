package com.joinai_support.service;

import com.joinai_support.domain.AuditLog;

import java.util.List;

public interface AuditLogService {
    void record(AuditLog auditLog);
    List<AuditLog> getRecentLogs(int page, int size);
}
