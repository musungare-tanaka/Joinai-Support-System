package com.joinai_support.service.serviceImpl;

import com.joinai_support.domain.AuditLog;
import com.joinai_support.repository.AuditLogRepository;
import com.joinai_support.service.AuditLogService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async
    @Override
    public void record(AuditLog auditLog) {
        auditLogRepository.save(auditLog);
    }

    @Override
    public List<AuditLog> getRecentLogs(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(1, size));
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable).getContent();
    }
}
