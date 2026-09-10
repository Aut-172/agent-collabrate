package com.example.agentcollab.service;

import com.example.agentcollab.client.AgentProviderClient;
import com.example.agentcollab.domain.*;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.AgentRunRepository;
import com.example.agentcollab.repository.DocumentVersionRepository;
import com.example.agentcollab.repository.OutboxJobRepository;
import com.example.agentcollab.repository.RepoInventoryVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class AgentRunService {
    private static final List<AgentRunStatus> ACTIVE_STATUSES =
            List.of(AgentRunStatus.QUEUED, AgentRunStatus.RUNNING);

    private final AgentRunRepository runs;
    private final OutboxJobRepository jobs;
    private final DocumentVersionRepository documents;
    private final WorkflowService workflowService;
    private final ProjectAccessService access;
    private final AgentProviderClient provider;
    private final RepoInventoryVersionRepository inventories;

    public AgentRunService(AgentRunRepository runs, OutboxJobRepository jobs,
                           DocumentVersionRepository documents, WorkflowService workflowService,
                           ProjectAccessService access, AgentProviderClient provider,
                           RepoInventoryVersionRepository inventories) {
        this.runs = runs;
        this.jobs = jobs;
        this.documents = documents;
        this.workflowService = workflowService;
        this.access = access;
        this.provider = provider;
        this.inventories = inventories;
    }

    @Transactional
    public AgentRun request(Long actorId, Long workflowId, AgentRunType runType) {
        Workflow workflow = workflowService.requireForUpdate(actorId, workflowId);
        workflowService.requireActiveProject(workflow);
        var active = runs.findTopByWorkflowIdAndRunTypeAndStatusInOrderByCreatedAtDesc(
                workflowId, runType, ACTIVE_STATUSES);
        if (active.isPresent()) return active.get();

        validateRequest(workflow, runType);
        AgentRun run = runs.save(new AgentRun(workflowId, runType, provider.providerName(),
                provider.modelName(), requestSummary(runType, workflow)));
        if (runType == AgentRunType.GENERATE_CODE_CONTEXT_PLAN) {
            Long inventoryId = inventories.findTopByProjectIdAndStatusOrderByCreatedAtDesc(
                    workflow.getProjectId(), RepoInventoryStatus.CURRENT).orElseThrow().getId();
            run.bindInventoryVersion(inventoryId);
        }
        jobs.save(new OutboxJob(OutboxJobType.AGENT_RUN, run.getId()));
        return run;
    }

    @Transactional(readOnly = true)
    public AgentRun get(Long actorId, Long runId) {
        AgentRun run = find(runId);
        workflowService.get(actorId, run.getWorkflowId());
        return run;
    }

    @Transactional
    public AgentRun retry(Long actorId, Long runId) {
        AgentRun previous = find(runId);
        workflowService.get(actorId, previous.getWorkflowId());
        if (previous.getStatus() != AgentRunStatus.FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, "AGENT_RUN_NOT_RETRYABLE", "只有失败的 AgentRun 可以重试");
        }
        return request(actorId, previous.getWorkflowId(), previous.getRunType());
    }

    @Transactional
    public AgentRun cancel(Long actorId, Long runId) {
        AgentRun visibleRun = find(runId);
        Workflow workflow = workflowService.get(actorId, visibleRun.getWorkflowId());
        access.requireLeader(workflow.getProjectId(), actorId);
        OutboxJob job = jobs.findByReferenceForUpdate(OutboxJobType.AGENT_RUN, runId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "OUTBOX_JOB_NOT_FOUND", "AgentRun 缺少后台任务记录"));
        AgentRun run = runs.findByIdForUpdate(runId).orElseThrow();
        if (!run.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "AGENT_RUN_NOT_CANCELLABLE", "当前 AgentRun 不能取消");
        }
        run.cancel();
        if (job.isActive()) job.fail("AgentRun cancelled");
        return run;
    }

    private void validateRequest(Workflow workflow, AgentRunType runType) {
        switch (runType) {
            case GENERATE_CODE_CONTEXT_PLAN -> {
                requireStatusIn(workflow, WorkflowStatus.INTENT, WorkflowStatus.DESIGN_PROPOSED,
                        WorkflowStatus.SPEC_PROPOSED, WorkflowStatus.SPEC_CONFIRMED,
                        WorkflowStatus.BUILD_PLAN_PROPOSED);
                inventories.findTopByProjectIdAndStatusOrderByCreatedAtDesc(
                                workflow.getProjectId(), RepoInventoryStatus.CURRENT)
                        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                                "REPO_INVENTORY_MISSING", "请先同步 Repo Inventory"));
            }
            case GENERATE_DESIGN -> {
                if (workflow.getIntentLevel() == IntentLevel.CHANGE) {
                    throw notAllowed("Change 直接生成 Build Plan，不生成 Design");
                }
                requireStatusIn(workflow, WorkflowStatus.INTENT, WorkflowStatus.DESIGN_PROPOSED);
            }
            case GENERATE_SPEC -> {
                if (workflow.getIntentLevel() == IntentLevel.CHANGE) {
                    throw notAllowed("Change 不生成 Spec");
                }
                requireStatusIn(workflow, WorkflowStatus.DESIGN_PROPOSED, WorkflowStatus.SPEC_PROPOSED);
                DocumentVersion design = documents
                        .findTopByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(workflow.getId(), DocumentType.DESIGN)
                        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                                "DOCUMENT_NOT_FOUND", "当前阶段缺少 Design"));
                if (!design.isConfirmed()) {
                    throw new ApiException(HttpStatus.CONFLICT,
                            "DOCUMENT_NOT_CONFIRMED", "当前 Design 版本尚未确认");
                }
            }
            case GENERATE_BUILD_PLAN -> {
                WorkflowStatus initialStatus = workflow.getIntentLevel() == IntentLevel.CHANGE
                        ? WorkflowStatus.INTENT : WorkflowStatus.SPEC_CONFIRMED;
                requireStatusIn(workflow, initialStatus, WorkflowStatus.BUILD_PLAN_PROPOSED);
            }
        }
    }

    private void requireStatusIn(Workflow workflow, WorkflowStatus... allowed) {
        if (!List.of(allowed).contains(workflow.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "WORKFLOW_STATE_CONFLICT",
                    "当前 Workflow 状态不允许该 Agent 操作");
        }
    }

    private ApiException notAllowed(String message) {
        return new ApiException(HttpStatus.CONFLICT, "AGENT_RUN_TYPE_NOT_ALLOWED", message);
    }

    private AgentRun find(Long runId) {
        return runs.findById(runId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AGENT_RUN_NOT_FOUND", "AgentRun 不存在"));
    }

    private String requestSummary(AgentRunType runType, Workflow workflow) {
        if (runType == AgentRunType.GENERATE_CODE_CONTEXT_PLAN) {
            Long inventoryId = inventories.findTopByProjectIdAndStatusOrderByCreatedAtDesc(
                    workflow.getProjectId(), RepoInventoryStatus.CURRENT).map(RepoInventoryVersion::getId).orElse(null);
            return runType + " for workflow " + workflow.getId() + ", inventoryVersionId=" + inventoryId;
        }
        return runType + " for workflow " + workflow.getId();
    }
}
