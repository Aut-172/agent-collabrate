package com.example.agentcollab.controller;

import com.example.agentcollab.dto.CodeContextDtos;
import com.example.agentcollab.security.CurrentUser;
import com.example.agentcollab.service.CodeContextService;
import com.example.agentcollab.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows/{workflowId}/code-context")
public class WorkflowCodeContextController {
    private final CodeContextService codeContext;
    private final UserService users;

    public WorkflowCodeContextController(CodeContextService codeContext, UserService users) {
        this.codeContext = codeContext; this.users = users;
    }

    @GetMapping
    public CodeContextDtos.ContextResponse get(@PathVariable Long workflowId) {
        return codeContext.workflowContext(users.requireByUsername(CurrentUser.username()).getId(), workflowId);
    }
}
