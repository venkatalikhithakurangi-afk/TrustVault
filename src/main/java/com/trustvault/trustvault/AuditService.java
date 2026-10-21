
package com.trustvault.trustvault;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void logAction(String username, String action, String documentName) {

        AuditLog auditLog = new AuditLog(
                username,
                action,
                documentName,
                LocalDateTime.now()
        );

        auditLogRepository.save(auditLog);
    }
}
