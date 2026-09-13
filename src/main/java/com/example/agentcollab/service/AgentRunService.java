package com.example.agentcollab.service;

import com.example.agentcollab.client.AgentProviderClient;
import com.example.agentcollab.domain.*;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.AgentRunRepository;
import com.example.agentcollab.repository.DocumentVersionRepository;
import com.example.agentcollab.repository.OutboxJobRepository;
import com.example.agentcollab.repository.RepoInventoryVersionRepository;
import com.example.agentcollab.repository.CodeContextPlanRepository;
import com.example.agentcollab.repository.CodeContextRunRepository;
import com.example.agentcollab.repository.CodeContextVersionRepository;
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
    private final CodeContextPlanRepository contextPlans;
    private final CodeContextVersionRepository contexts;
    private final CodeContextRunRepository contextRuns;

    public AgentRunService(AgentRunRepository runs, OutboxJobRepository jobs,
                           DocumentVersionRepository documents, WorkflowService workflowService,
                           ProjectAccessService access, AgentProviderClient provider,
                           RepoInventoryVersionRepository inventories, CodeContextPlanRepository contextPlans,
                           CodeContextVersionRepository contexts, CodeContextRunRepository contextRuns) {
        this.runs = runs;
        this.jobs = jobs;
        this.documents = documents;
        this.workflowService = workflowService;
        this.access = access;
        this.provider = provider;
        this.inventories = inventories;
        this.contextPlans = contextPlans;
        this.contexts = contexts;
        this.contextRuns = contextRuns;
    }

    @Transactional
    public AgentRun request(Long actorId, Long workflowId, AgentRunType runType) {
        Workflow workflow = workflowService.requireForUpdate(actorId, workflowId);
        workflowService.requireActiveProject(workflow);
        var active = runs.findTopByWorkflowIdAndRunTypeAndStatusInOrderByCreatedAtDesc(
                workflowId, runType, ACTIVE_STATUSES);
        if (active.isPresent()) return active.get();

        validateRequest(workflow, runType);
        GenerationContext generationContext = resolveGenerationContext(workflow, runType);
        AgentRun run = runs.save(new AgentRun(workflowId, runType, provider.providerName(),
                provider.modelName(), requestSummary(runType, workflow, generationContext)));
        if (runType == AgentRunType.GENERATE_CODE_CONTEXT_PLAN) {
            run.bindInventoryVersion(generationContext.inventoryVersionId());
        } else {
            run.bindGenerationContext(generationContext.inventoryVersionId(), generationContext.contextPlanId(),
                    generationContext.codeContextVersionId());
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

    @Transactional(readOnly = true)
    public List<AgentRun> activeForWorkflow(Long actorId, Long workflowId) {
        workflowService.get(actorId, workflowId);
        return runs.findByWorkflowIdAndStatusIn(workflowId, ACTIVE_STATUSES);
    }

    @Transactional(readOnly = true)
    public List<AgentRun> latestForWorkflow(Long actorId, Long workflowId) {
        workflowService.get(actorId, workflowId);
        return runs.findByWorkflowIdOrderByCreatedAtDesc(workflowId);
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
                        WorkflowStatus.DESIGN_CONFIRMED, WorkflowStatus.SPEC_PROPOSED, WorkflowStatus.SPEC_CONFIRMED,
                        WorkflowStatus.BUILD_PLAN_PROPOSED);
                if (contextRuns.findTopByProjectIdAndRunTypeAndStatusInOrderByCreatedAtDesc(
                        workflow.getProjectId(), CodeContextRun.Type.REPO_INGESTION,
                        List.of(CodeContextRunStatus.QUEUED, CodeContextRunStatus.RUNNING)).isPresent()) {
                    throw new ApiException(HttpStatus.CONFLICT, "REPO_INVENTORY_REFRESH_PENDING",
                            "Workflow 创建后的 Repo Inventory 正在刷新，请等待同步完成");
                }
                inventories.findTopByProjectIdAndStatusOrderByCreatedAtDesc(
                                workflow.getProjectId(), RepoInventoryStatus.CURRENT)
                        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                                "REPO_INVENTORY_REFRESH_REQUIRED", "请先完成 Repo Inventory 刷新"));
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
                DocumentVersion design = documents
                        .findTopByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(workflow.getId(), DocumentType.DESIGN)
                        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                                "DOCUMENT_NOT_FOUND", "当前阶段缺少 Design"));
                if (!design.isConfirmed()) {
                    throw new ApiException(HttpStatus.CONFLICT,
                            "DOCUMENT_NOT_CONFIRMED", "当前 Design 版本尚未确认");
                }
                requireStatusIn(workflow, WorkflowStatus.DESIGN_CONFIRMED, WorkflowStatus.SPEC_PROPOSED);
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

    private GenerationContext resolveGenerationContext(Workflow workflow, AgentRunType runType) {
        if (runType == AgentRunType.GENERATE_CODE_CONTEXT_PLAN) {
            Long inventoryId = inventories.findTopByProjectIdAndStatusOrderByCreatedAtDesc(
                    workflow.getProjectId(), RepoInventoryStatus.CURRENT).orElseThrow().getId();
            return new GenerationContext(inventoryId, null, null);
        }
        CodeContextPlan plan = contextPlans.findTopByWorkflowIdOrderByCreatedAtDesc(workflow.getId())
                .orElseThrow(() -> codeContextConflict("CODE_CONTEXT_REQUIRED",
                        "请先为当前 Workflow 刷新 Code Context"));
        if (!workflow.getProjectId().equals(plan.getProjectId()) || plan.getStatus() != CodeContextPlanStatus.USED) {
            throw codeContextConflict("CODE_CONTEXT_REQUIRED", "当前 Workflow 的 Context Plan 尚未完成取证");
        }
        CodeContextVersion context = contexts.findTopByContextPlanIdOrderByCreatedAtDesc(plan.getId())
                .orElseThrow(() -> codeContextConflict("CODE_CONTEXT_REQUIRED",
                        "请等待当前 Workflow 的 Code Context 取证完成"));
        RepoInventoryVersion inventory = inventories.findById(context.getInventoryVersionId())
                .orElseThrow(() -> codeContextConflict("CODE_CONTEXT_STALE", "Code Context 对应的 Inventory 不存在"));
        if (!workflow.getProjectId().equals(context.getProjectId())
                || context.getStatus() != CodeContextStatus.CURRENT
                || inventory.getStatus() != RepoInventoryStatus.CURRENT
                || !context.getBaseCommitSha().equalsIgnoreCase(inventory.getCommitSha())) {
            throw codeContextConflict("CODE_CONTEXT_STALE", "当前 Workflow 的 Code Context 已过期，请刷新");
        }
        return new GenerationContext(inventory.getId(), plan.getId(), context.getId());
    }

    private String requestSummary(AgentRunType runType, Workflow workflow, GenerationContext context) {
        String summary = runType + " for workflow " + workflow.getId()
                + ", inventoryVersionId=" + context.inventoryVersionId();
        if (context.codeContextVersionId() != null) {
            summary += ", contextPlanId=" + context.contextPlanId()
                    + ", codeContextVersionId=" + context.codeContextVersionId();
        }
        return summary;
    }

    private ApiException codeContextConflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private record GenerationContext(Long inventoryVersionId, Long contextPlanId, Long codeContextVersionId) {}
}
