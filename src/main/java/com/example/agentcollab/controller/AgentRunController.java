package com.example.agentcollab.controller;

import com.example.agentcollab.dto.AgentRunDtos;
import com.example.agentcollab.security.CurrentUser;
import com.example.agentcollab.service.AgentRunService;
import com.example.agentcollab.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent-runs")
public class AgentRunController {
    private final AgentRunService agentRuns;
    private final UserService users;

    public AgentRunController(AgentRunService agentRuns, UserService users) {
        this.agentRuns = agentRuns;
        this.users = users;
    }

    @GetMapping("/{runId}")
    public AgentRunDtos.AgentRunResponse get(@PathVariable Long runId) {
        return AgentRunDtos.AgentRunResponse.from(agentRuns.get(currentUserId(), runId));
    }

    @PostMapping("/{runId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AgentRunDtos.EnqueuedRunResponse retry(@PathVariable Long runId) {
        return AgentRunDtos.EnqueuedRunResponse.from(agentRuns.retry(currentUserId(), runId));
    }

    @PostMapping("/{runId}/cancel")
    public AgentRunDtos.AgentRunResponse cancel(@PathVariable Long runId) {
        return AgentRunDtos.AgentRunResponse.from(agentRuns.cancel(currentUserId(), runId));
    }

    private Long currentUserId() {
        return users.requireByUsername(CurrentUser.username()).getId();
    }
}
