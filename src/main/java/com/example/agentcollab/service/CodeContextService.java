package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.dto.CodeContextDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CodeContextService {
    private static final List<CodeContextRunStatus> ACTIVE_RUN_STATUSES =
            List.of(CodeContextRunStatus.QUEUED, CodeContextRunStatus.RUNNING);

    private final CodeContextRunRepository runs;
    private final RepoInventoryVersionRepository inventories;
    private final RepoInventoryFileRepository files;
    private final ProjectRepository projects;
    private final OutboxJobRepository jobs;
    private final ProjectAccessService access;
    private final CodeContextVersionRepository contexts;
    private final CodeContextFileRepository evidenceFiles;
    private final CodeContextPlanRepository plans;
    private final WorkflowService workflowService;

    public CodeContextService(CodeContextRunRepository runs, RepoInventoryVersionRepository inventories,
                              RepoInventoryFileRepository files, ProjectRepository projects,
                              OutboxJobRepository jobs, ProjectAccessService access,
                              CodeContextVersionRepository contexts, CodeContextFileRepository evidenceFiles,
                              CodeContextPlanRepository plans, WorkflowService workflowService) {
        this.runs = runs;
        this.inventories = inventories;
        this.files = files;
        this.projects = projects;
        this.jobs = jobs;
        this.access = access;
        this.contexts = contexts; this.evidenceFiles = evidenceFiles; this.plans = plans;
        this.workflowService = workflowService;
    }

    @Transactional
    public CodeContextRun requestSync(Long actorId, Long projectId) {
        access.requireLeader(projectId, actorId);
        Project project = projects.findByIdForUpdate(projectId)
                .orElseThrow(() -> notFound("PROJECT_NOT_FOUND", "项目不存在或无权访问"));
        if (project.getStatus() != Project.Status.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_ARCHIVED", "归档项目不能同步代码上下文");
        }
        var active = runs.findTopByProjectIdAndRunTypeAndStatusInOrderByCreatedAtDesc(
                projectId, CodeContextRun.Type.REPO_INGESTION, ACTIVE_RUN_STATUSES);
        if (active.isPresent()) return active.get();
        CodeContextRun run = runs.save(new CodeContextRun(projectId, actorId));
        jobs.save(new OutboxJob(OutboxJobType.CODE_CONTEXT_SYNC, run.getId()));
        return run;
    }

    @Transactional(readOnly = true)
    public CodeContextRun getRun(Long actorId, Long projectId, Long runId) {
        access.requireMember(projectId, actorId);
        return runs.findById(runId).filter(run -> run.getProjectId().equals(projectId))
                .orElseThrow(() -> notFound("CODE_CONTEXT_RUN_NOT_FOUND", "代码上下文运行记录不存在"));
    }

    @Transactional(readOnly = true)
    public CodeContextDtos.InventoryResponse latestInventory(Long actorId, Long projectId) {
        access.requireMember(projectId, actorId);
        RepoInventoryVersion inventory = inventories
                .findTopByProjectIdAndStatusOrderByCreatedAtDesc(projectId, RepoInventoryStatus.CURRENT)
                .orElseThrow(() -> notFound("REPO_INVENTORY_NOT_FOUND", "当前仓库索引不存在"));
        return response(inventory);
    }

    @Transactional(readOnly = true)
    public CodeContextDtos.ContextResponse latestContext(Long actorId, Long projectId) {
        access.requireMember(projectId, actorId);
        return contextResponse(contexts.findTopByProjectIdAndStatusOrderByCreatedAtDesc(
                projectId, CodeContextStatus.CURRENT).orElseThrow(() ->
                notFound("CODE_CONTEXT_NOT_FOUND", "当前项目缺少可用 Code Context")));
    }

    @Transactional(readOnly = true)
    public CodeContextDtos.ContextResponse workflowContext(Long actorId, Long workflowId) {
        Workflow workflow = workflowService.get(actorId, workflowId);
        CodeContextPlan plan = plans.findTopByWorkflowIdOrderByCreatedAtDesc(workflowId)
                .orElseThrow(() -> notFound("CODE_CONTEXT_NOT_FOUND", "当前 Workflow 缺少 Code Context"));
        return contextResponse(contexts.findTopByContextPlanIdAndStatusOrderByCreatedAtDesc(
                plan.getId(), CodeContextStatus.CURRENT).orElseThrow(() ->
                notFound("CODE_CONTEXT_NOT_FOUND", "当前 Workflow 缺少可用 Code Context")));
    }

    private CodeContextDtos.ContextResponse contextResponse(CodeContextVersion context) {
        var files = evidenceFiles.findByContextVersionIdOrderByPath(context.getId()).stream()
                .map(CodeContextDtos.EvidenceFileResponse::from).toList();
        return new CodeContextDtos.ContextResponse(context.getId(), context.getProjectId(),
                context.getInventoryVersionId(), context.getContextPlanId(), context.getProvider(),
                context.getRepositoryUrl(), context.getBranchName(), context.getBaseCommitSha(),
                context.getStatus(), context.getRepositoryProfile(), context.getEvidenceJson(), files,
                context.getCreatedAt(), context.getUpdatedAt());
    }

    private CodeContextDtos.InventoryResponse response(RepoInventoryVersion inventory) {
        var indexedFiles = files.findByInventoryVersionIdOrderByPath(inventory.getId()).stream()
                .map(CodeContextDtos.InventoryFileResponse::from).toList();
        return new CodeContextDtos.InventoryResponse(inventory.getId(), inventory.getProjectId(),
                inventory.getProvider(), inventory.getRepositoryUrl(), inventory.getBranchName(),
                inventory.getCommitSha(), inventory.getStatus(), inventory.getRepositoryProfile(),
                inventory.getTreeSummary(), indexedFiles, inventory.getCreatedAt(), inventory.getUpdatedAt());
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
