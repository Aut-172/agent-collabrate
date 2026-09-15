package com.example.agentcollab.controller;

import com.example.agentcollab.domain.DocumentType;
import com.example.agentcollab.domain.AgentRunType;
import com.example.agentcollab.dto.AgentRunDtos;
import com.example.agentcollab.dto.PlanDtos;
import com.example.agentcollab.dto.TaskDtos;
import com.example.agentcollab.dto.WorkflowDtos;
import com.example.agentcollab.dto.DocumentDecisionDtos;
import com.example.agentcollab.security.CurrentUser;
import com.example.agentcollab.service.DocumentService;
import com.example.agentcollab.service.AgentRunService;
import com.example.agentcollab.service.PlanService;
import com.example.agentcollab.service.TaskService;
import com.example.agentcollab.service.UserService;
import com.example.agentcollab.service.WorkflowService;
import com.example.agentcollab.service.WorkflowBoardService;
import com.example.agentcollab.service.CiSyncService;
import com.example.agentcollab.service.DocumentDecisionService;
import com.example.agentcollab.dto.WorkflowBoardDtos;
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
    private final AgentRunService agentRunService;
    private final PlanService planService;
    private final TaskService taskService;
    private final WorkflowBoardService boardService;
    private final CiSyncService ciSyncService;
    private final DocumentDecisionService decisionService;

    public WorkflowController(WorkflowService workflowService, DocumentService documentService,
                              UserService userService, AgentRunService agentRunService,
                              PlanService planService, TaskService taskService,
                              WorkflowBoardService boardService, CiSyncService ciSyncService,
                              DocumentDecisionService decisionService) {
        this.workflowService = workflowService;
        this.documentService = documentService;
        this.userService = userService;
        this.agentRunService = agentRunService;
        this.planService = planService;
        this.taskService = taskService;
        this.boardService = boardService;
        this.ciSyncService = ciSyncService;
        this.decisionService = decisionService;
    }

    @PostMapping("/workflows/{workflowId}/generate-design")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AgentRunDtos.EnqueuedRunResponse generateDesign(@PathVariable Long workflowId) {
        return AgentRunDtos.EnqueuedRunResponse.from(agentRunService.request(
                currentUserId(), workflowId, AgentRunType.GENERATE_DESIGN));
    }

    @PostMapping("/workflows/{workflowId}/generate-spec")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AgentRunDtos.EnqueuedRunResponse generateSpec(@PathVariable Long workflowId) {
        return AgentRunDtos.EnqueuedRunResponse.from(agentRunService.request(
                currentUserId(), workflowId, AgentRunType.GENERATE_SPEC));
    }

    @PostMapping("/workflows/{workflowId}/generate-build-plan")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AgentRunDtos.EnqueuedRunResponse generateBuildPlan(@PathVariable Long workflowId) {
        return AgentRunDtos.EnqueuedRunResponse.from(agentRunService.request(
                currentUserId(), workflowId, AgentRunType.GENERATE_BUILD_PLAN));
    }

    @PostMapping("/workflows/{workflowId}/code-context/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AgentRunDtos.EnqueuedRunResponse refreshCodeContext(@PathVariable Long workflowId) {
        return AgentRunDtos.EnqueuedRunResponse.from(agentRunService.request(
                currentUserId(), workflowId, AgentRunType.GENERATE_CODE_CONTEXT_PLAN));
    }

    @PutMapping("/workflows/{workflowId}/plan-drafts")
    public WorkflowDtos.DocumentResponse savePlanDraft(
            @PathVariable Long workflowId,
            @Valid @RequestBody WorkflowDtos.SaveDocumentRequest request) {
        return WorkflowDtos.DocumentResponse.from(
                planService.saveDraft(currentUserId(), workflowId, request.content()));
    }

    @PostMapping("/workflows/{workflowId}/approve-plan")
    public WorkflowDtos.DocumentResponse approvePlan(
            @PathVariable Long workflowId,
            @Valid @RequestBody WorkflowDtos.ConfirmDocumentRequest request) {
        return WorkflowDtos.DocumentResponse.from(
                planService.approve(currentUserId(), workflowId, request.versionNo(), request.reason()));
    }

    @GetMapping("/workflows/{workflowId}/plan-granularity")
    public PlanDtos.GranularityResponse planGranularity(@PathVariable Long workflowId) {
        return planService.granularity(currentUserId(), workflowId);
    }

    @PostMapping("/workflows/{workflowId}/plan-granularity")
    public WorkflowDtos.DocumentResponse mergePlanTasks(@PathVariable Long workflowId,
                                                         @Valid @RequestBody PlanDtos.TaskMergeRequest request) {
        return WorkflowDtos.DocumentResponse.from(planService.applyGranularityDecision(
                currentUserId(), workflowId, request));
    }

    @PostMapping("/workflows/{workflowId}/create-tasks")
    public PlanDtos.CreationResponse createTasks(@PathVariable Long workflowId) {
        return taskService.createFromApprovedPlan(currentUserId(), workflowId);
    }

    @GetMapping("/workflows/{workflowId}/tasks")
    public List<TaskDtos.TaskResponse> tasks(@PathVariable Long workflowId) {
        return taskService.list(currentUserId(), workflowId);
    }

    @GetMapping("/workflows/{workflowId}/board")
    public WorkflowBoardDtos.BoardResponse board(@PathVariable Long workflowId) {
        return boardService.get(currentUserId(), workflowId);
    }

    @GetMapping("/projects/{projectId}/board")
    public WorkflowBoardDtos.ProjectBoardResponse projectBoard(@PathVariable Long projectId) {
        return boardService.getForProject(currentUserId(), projectId);
    }

    @PostMapping("/projects/{projectId}/workflows")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowDtos.WorkflowResponse create(@PathVariable Long projectId,
                                                 @Valid @RequestBody WorkflowDtos.CreateWorkflowRequest request) {
        return WorkflowDtos.WorkflowResponse.from(workflowService.create(currentUserId(), projectId, request));
    }

    @PostMapping("/projects/{projectId}/ci-bootstrap")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowDtos.WorkflowResponse createCiBootstrap(
            @PathVariable Long projectId,
            @Valid @RequestBody WorkflowDtos.CreateCiBootstrapRequest request) {
        return WorkflowDtos.WorkflowResponse.from(
                workflowService.createCiBootstrap(currentUserId(), projectId, request));
    }

    @GetMapping("/workflows")
    public List<WorkflowDtos.WorkflowResponse> list() {
        return workflowService.listForUser(currentUserId()).stream().map(WorkflowDtos.WorkflowResponse::from).toList();
    }

    @GetMapping("/workflows/{workflowId}")
    public WorkflowDtos.WorkflowResponse get(@PathVariable Long workflowId) {
        ciSyncService.reconcileWorkflow(currentUserId(), workflowId);
        Long actorId = currentUserId();
        var workflow = workflowService.get(actorId, workflowId);
        var activeRuns = agentRunService.activeForWorkflow(actorId, workflowId).stream()
                .map(AgentRunDtos.AgentRunResponse::from).toList();
        var latestRuns = agentRunService.latestForWorkflow(actorId, workflowId).stream()
                .limit(20)
                .map(AgentRunDtos.AgentRunResponse::from).toList();
        return WorkflowDtos.WorkflowResponse.from(workflow, activeRuns, latestRuns);
    }

    @GetMapping("/workflows/{workflowId}/documents")
    public List<WorkflowDtos.DocumentResponse> documents(@PathVariable Long workflowId,
                                                          @RequestParam(required = false) DocumentType type) {
        return documentService.list(currentUserId(), workflowId, type).stream()
                .map(WorkflowDtos.DocumentResponse::from).toList();
    }

    @GetMapping("/workflows/{workflowId}/decisions")
    public List<DocumentDecisionDtos.DecisionResponse> decisions(@PathVariable Long workflowId) {
        return decisionService.listCurrent(currentUserId(), workflowId);
    }

    @PostMapping("/workflows/{workflowId}/decisions/{decisionId}/resolve")
    public DocumentDecisionDtos.DecisionResponse resolveDecision(
            @PathVariable Long workflowId,
            @PathVariable Long decisionId,
            @Valid @RequestBody DocumentDecisionDtos.ResolveRequest request) {
        return decisionService.resolve(currentUserId(), workflowId, decisionId, request.selectedOption());
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

    @PostMapping("/workflows/{workflowId}/close")
    public WorkflowDtos.WorkflowResponse close(@PathVariable Long workflowId) {
        return WorkflowDtos.WorkflowResponse.from(workflowService.close(currentUserId(), workflowId));
    }

    private Long currentUserId() {
        return userService.requireByUsername(CurrentUser.username()).getId();
    }
}
