package com.example.agentcollab.controller;

import com.example.agentcollab.dto.AgentCallLogDtos;
import com.example.agentcollab.security.CurrentUser;
import com.example.agentcollab.service.AgentCallLogService;
import com.example.agentcollab.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/agent-call-logs")
public class AgentCallLogController {
    private final AgentCallLogService logs;
    private final UserService users;

    public AgentCallLogController(AgentCallLogService logs, UserService users) {
        this.logs = logs;
        this.users = users;
    }

    @GetMapping
    public AgentCallLogDtos.Response list(@PathVariable Long projectId) {
        return logs.list(currentUserId(), projectId);
    }

    private Long currentUserId() {
        return users.requireByUsername(CurrentUser.username()).getId();
    }
}
