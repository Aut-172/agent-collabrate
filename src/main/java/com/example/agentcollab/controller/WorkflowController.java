package com.example.agentcollab.controller;

import com.example.agentcollab.domain.DocumentType;
import com.example.agentcollab.dto.WorkflowDtos;
import com.example.agentcollab.security.CurrentUser;
import com.example.agentcollab.service.DocumentService;
import com.example.agentcollab.service.UserService;
import com.example.agentcollab.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class WorkflowController {
    private final WorkflowService workflowService;
    private final DocumentService documentService;
    private final UserService userService;

    public WorkflowController(WorkflowService workflowService, DocumentService documentService, UserService userService) {
        this.workflowService = workflowService;
        this.documentService = documentService;
        this.userService = userService;
    }

    @PostMapping("/projects/{projectId}/workflows")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowDtos.WorkflowResponse create(@PathVariable Long projectId,
                                                 @Valid @RequestBody WorkflowDtos.CreateWorkflowRequest request) {
        return WorkflowDtos.WorkflowResponse.from(workflowService.create(currentUserId(), projectId, request));
    }

    @GetMapping("/workflows")
    public List<WorkflowDtos.WorkflowResponse> list() {
        return workflowService.listForUser(currentUserId()).stream().map(WorkflowDtos.WorkflowResponse::from).toList();
    }

    @GetMapping("/workflows/{workflowId}")
    public WorkflowDtos.WorkflowResponse get(@PathVariable Long workflowId) {
        return WorkflowDtos.WorkflowResponse.from(workflowService.get(currentUserId(), workflowId));
    }

    @GetMapping("/workflows/{workflowId}/documents")
    public List<WorkflowDtos.DocumentResponse> documents(@PathVariable Long workflowId,
                                                          @RequestParam(required = false) DocumentType type) {
        return documentService.list(currentUserId(), workflowId, type).stream()
                .map(WorkflowDtos.DocumentResponse::from).toList();
    }

    @PutMapping("/workflows/{workflowId}/design")
    public WorkflowDtos.DocumentResponse saveDesign(@PathVariable Long workflowId,
                                                     @Valid @RequestBody WorkflowDtos.SaveDocumentRequest request) {
        return WorkflowDtos.DocumentResponse.from(
                documentService.saveDesignEdit(currentUserId(), workflowId, request.content()));
    }

    @PostMapping("/workflows/{workflowId}/confirm-design")
    public WorkflowDtos.DocumentResponse confirmDesign(@PathVariable Long workflowId,
                                                        @Valid @RequestBody WorkflowDtos.ConfirmDocumentRequest request) {
        return WorkflowDtos.DocumentResponse.from(
                documentService.confirmDesign(currentUserId(), workflowId, request.versionNo()));
    }

    @PutMapping("/workflows/{workflowId}/spec")
    public WorkflowDtos.DocumentResponse saveSpec(@PathVariable Long workflowId,
                                                   @Valid @RequestBody WorkflowDtos.SaveDocumentRequest request) {
        return WorkflowDtos.DocumentResponse.from(
                documentService.saveSpecEdit(currentUserId(), workflowId, request.content()));
    }

    @PostMapping("/workflows/{workflowId}/confirm-spec")
    public WorkflowDtos.DocumentResponse confirmSpec(@PathVariable Long workflowId,
                                                      @Valid @RequestBody WorkflowDtos.ConfirmDocumentRequest request) {
        return WorkflowDtos.DocumentResponse.from(
                documentService.confirmSpec(currentUserId(), workflowId, request.versionNo()));
    }

    @PostMapping("/workflows/{workflowId}/cancel")
    public WorkflowDtos.WorkflowResponse cancel(@PathVariable Long workflowId) {
        return WorkflowDtos.WorkflowResponse.from(workflowService.cancel(currentUserId(), workflowId));
    }

    private Long currentUserId() {
        return userService.requireByUsername(CurrentUser.username()).getId();
    }
}
