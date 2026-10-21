
package com.trustvault.trustvault;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminAuditController {

    private final AuditLogRepository auditLogRepository;

    public AdminAuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/admin/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public String showAuditLogs(Model model) {

        model.addAttribute(
                "auditLogs",
                auditLogRepository.findAllByOrderByTimestampDesc()
        );

        return "audit-logs";
    }
}

