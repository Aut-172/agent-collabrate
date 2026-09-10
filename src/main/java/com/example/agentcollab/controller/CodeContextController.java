package com.example.agentcollab.controller;

import com.example.agentcollab.dto.CodeContextDtos;
import com.example.agentcollab.security.CurrentUser;
import com.example.agentcollab.service.CodeContextService;
import com.example.agentcollab.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class CodeContextController {
    private final CodeContextService codeContext;
    private final UserService users;

    public CodeContextController(CodeContextService codeContext, UserService users) {
        this.codeContext = codeContext;
        this.users = users;
    }

    @PostMapping("/code-context/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CodeContextDtos.SyncResponse sync(@PathVariable Long projectId) {
        return CodeContextDtos.SyncResponse.from(codeContext.requestSync(userId(), projectId));
    }

    @GetMapping("/code-context/runs/{runId}")
    public CodeContextDtos.RunResponse run(@PathVariable Long projectId, @PathVariable Long runId) {
        return CodeContextDtos.RunResponse.from(codeContext.getRun(userId(), projectId, runId));
    }

    @GetMapping("/repo-inventory/latest")
    public CodeContextDtos.InventoryResponse latestInventory(@PathVariable Long projectId) {
        return codeContext.latestInventory(userId(), projectId);
    }

    @GetMapping("/code-context/latest")
    public CodeContextDtos.ContextResponse latestContext(@PathVariable Long projectId) {
        return codeContext.latestContext(userId(), projectId);
    }

    private Long userId() {
        return users.requireByUsername(CurrentUser.username()).getId();
    }
}
