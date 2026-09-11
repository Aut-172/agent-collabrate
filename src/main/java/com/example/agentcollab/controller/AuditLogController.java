package com.example.agentcollab.controller;

import com.example.agentcollab.dto.AuditLogDtos;
import com.example.agentcollab.security.CurrentUser;
import com.example.agentcollab.service.AuditLogService;
import com.example.agentcollab.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {
    private final AuditLogService audits; private final UserService users;
    public AuditLogController(AuditLogService audits, UserService users) { this.audits = audits; this.users = users; }
    @GetMapping
    public Page<AuditLogDtos.AuditLogResponse> list(@RequestParam(required = false) Long projectId,
                                                    @RequestParam(required = false) String entityType,
                                                    @RequestParam(required = false) Long entityId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "50") int size) {
        return audits.list(currentUserId(), projectId, entityType, entityId, page, size);
    }
    private Long currentUserId() { return users.requireByUsername(CurrentUser.username()).getId(); }
}
