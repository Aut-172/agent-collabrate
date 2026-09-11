package com.example.agentcollab.controller;

import com.example.agentcollab.dto.AuditLogDtos;
import com.example.agentcollab.security.CurrentUser;
import com.example.agentcollab.service.AuditLogService;
import com.example.agentcollab.service.UserService;
import com.example.agentcollab.repository.WorkflowRepository;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows/{workflowId}/audit-logs")
public class WorkflowAuditLogController {
    private final AuditLogService audits; private final UserService users; private final WorkflowRepository workflows;
    public WorkflowAuditLogController(AuditLogService audits, UserService users, WorkflowRepository workflows) { this.audits = audits; this.users = users; this.workflows = workflows; }
    @GetMapping
    public Page<AuditLogDtos.AuditLogResponse> list(@PathVariable Long workflowId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "50") int size) {
        Long actor = users.requireByUsername(CurrentUser.username()).getId();
        var workflow = workflows.findById(workflowId).orElseThrow(() -> new com.example.agentcollab.exception.ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "WORKFLOW_NOT_FOUND", "Workflow 不存在"));
        return audits.list(actor, workflow.getProjectId(), "WORKFLOW", workflowId, page, size);
    }
}
