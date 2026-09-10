package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.dto.TaskPackageDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service
public class TaskPackageConfirmationService {
    private final TaskRepository tasks;
    private final TaskPackageRepository packages;
    private final TaskPackageConfirmationRepository confirmations;
    private final TaskAssignmentRepository assignments;
    private final WorkflowService workflowService;
    private final WorkflowRepository workflows;
    private final WorkflowStateMachine stateMachine;
    private final CodeContextVersionRepository contexts;
    private final CodeContextPlanRepository contextPlans;
    private final RepoInventoryVersionRepository inventories;

    public TaskPackageConfirmationService(TaskRepository tasks, TaskPackageRepository packages,
                                          TaskPackageConfirmationRepository confirmations,
                                          TaskAssignmentRepository assignments, WorkflowService workflowService,
                                          WorkflowRepository workflows, WorkflowStateMachine stateMachine,
                                          CodeContextVersionRepository contexts,
                                          CodeContextPlanRepository contextPlans,
                                          RepoInventoryVersionRepository inventories) {
        this.tasks = tasks;
        this.packages = packages;
        this.confirmations = confirmations;
        this.assignments = assignments;
        this.workflowService = workflowService;
        this.workflows = workflows;
        this.stateMachine = stateMachine;
        this.contexts = contexts;
        this.contextPlans = contextPlans;
        this.inventories = inventories;
    }

    @Transactional
    public TaskPackageDtos.ConfirmationResponse confirm(Long actorId, Long taskId, int packageVersion,
                                                        TaskPackageDtos.ConfirmRequest request) {
        Task task = tasks.findByIdForUpdate(taskId)
                .orElseThrow(() -> notFound("TASK_NOT_FOUND", "Task 不存在"));
        Workflow workflow = workflowService.requireForUpdate(actorId, task.getWorkflowId());
        workflowService.requireActiveProject(workflow);
        TaskAssignment assignment = assignments.findCurrentForUpdate(taskId)
                .orElseThrow(() -> conflict("TASK_NOT_ASSIGNED", "Task 尚未分配负责人"));
        if (!assignment.getAssigneeUserId().equals(actorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_ASSIGNEE_REQUIRED", "只有当前负责人可以确认任务包");
        }

        TaskPackage current = packages.findByTaskIdAndStatus(taskId, TaskPackageStatus.CURRENT)
                .orElseThrow(() -> notFound("TASK_PACKAGE_NOT_FOUND", "当前任务包不存在"));
        requireCurrentCodeContext(current, workflow);
        if (current.getPackageVersion() != packageVersion
                || !current.getId().equals(request.packageId())
                || !current.getContentHash().equals(request.contentHash())
                || !Integer.valueOf(packageVersion).equals(task.getCurrentPackageVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "TASK_PACKAGE_STALE", "提交的任务包不是当前有效版本",
                    Map.of("currentVersion", current.getPackageVersion(),
                            "submittedVersion", packageVersion,
                            "latestPackageUrl", "/api/tasks/" + taskId + "/packages/current",
                            "diffUrl", "/api/tasks/" + taskId + "/packages/diff?from="
                                    + packageVersion + "&to=" + current.getPackageVersion()));
        }
        var existing = confirmations.findByTaskIdAndUserIdAndPackageVersion(taskId, actorId, packageVersion);
        if (task.getStatus() == TaskStatus.IN_PROGRESS && existing.isPresent()) {
            return response(existing.orElseThrow(), task);
        }
        if (task.getStatus() != TaskStatus.ASSIGNED) {
            throw conflict("TASK_STATE_CONFLICT", "当前 Task 状态不允许开始开发");
        }

        TaskPackageConfirmation confirmation = existing
                .orElseGet(() -> confirmations.save(new TaskPackageConfirmation(
                        taskId, current.getId(), packageVersion, current.getContentHash(), actorId,
                        TaskPackageConfirmation.Type.START_DEVELOPMENT)));
        task.startDevelopment();
        tasks.save(task);
        if (workflow.getStatus() == WorkflowStatus.TASKS_READY) {
            stateMachine.transition(workflow, WorkflowStatus.IN_PROGRESS);
            workflows.save(workflow);
        }
        return response(confirmation, task);
    }

    private void requireCurrentCodeContext(TaskPackage taskPackage, Workflow workflow) {
        if (taskPackage.getCodeContextVersionId() == null || taskPackage.getContextPlanId() == null) {
            throw conflict("TASK_PACKAGE_CONTEXT_STALE", "任务包缺少可追溯 Code Context，请重新生成");
        }
        CodeContextVersion context = contexts.findById(taskPackage.getCodeContextVersionId())
                .orElseThrow(() -> conflict("TASK_PACKAGE_CONTEXT_STALE", "任务包的 Code Context 不存在"));
        if (context.getInventoryVersionId() == null || context.getContextPlanId() == null) {
            throw conflict("TASK_PACKAGE_CONTEXT_STALE", "任务包的 Code Context 缺少取证链路");
        }
        RepoInventoryVersion inventory = inventories.findById(context.getInventoryVersionId())
                .orElseThrow(() -> conflict("TASK_PACKAGE_CONTEXT_STALE", "任务包的 Repo Inventory 不存在"));
        CodeContextPlan contextPlan = contextPlans.findById(taskPackage.getContextPlanId())
                .orElseThrow(() -> conflict("TASK_PACKAGE_CONTEXT_STALE", "任务包的 Context Plan 不存在"));
        if (!workflow.getProjectId().equals(context.getProjectId())
                || !workflow.getProjectId().equals(inventory.getProjectId())
                || !workflow.getProjectId().equals(contextPlan.getProjectId())
                || !workflow.getId().equals(contextPlan.getWorkflowId())
                || context.getStatus() != CodeContextStatus.CURRENT
                || inventory.getStatus() != RepoInventoryStatus.CURRENT
                || !taskPackage.getContextPlanId().equals(context.getContextPlanId())
                || !context.getInventoryVersionId().equals(contextPlan.getInventoryVersionId())
                || contextPlan.getStatus() != CodeContextPlanStatus.USED
                || !taskPackage.getBaseCommit().equalsIgnoreCase(context.getBaseCommitSha())
                || !taskPackage.getBaseCommit().equalsIgnoreCase(inventory.getCommitSha())) {
            throw conflict("TASK_PACKAGE_CONTEXT_STALE", "任务包的 Code Context 已过期，请重新生成");
        }
    }

    private TaskPackageDtos.ConfirmationResponse response(TaskPackageConfirmation confirmation, Task task) {
        return new TaskPackageDtos.ConfirmationResponse(confirmation.getId(), confirmation.getTaskId(),
                confirmation.getPackageId(), confirmation.getPackageVersion(), confirmation.getContentHash(),
                confirmation.getUserId(), confirmation.getConfirmationType().name(),
                confirmation.getCreatedAt(), task.getStatus());
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
