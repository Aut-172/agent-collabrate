package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.dto.TaskDeliveryDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class TaskDeliveryService {
    private final TaskRepository tasks;
    private final TaskDeliveryRepository deliveries;
    private final TaskAssignmentRepository assignments;
    private final TaskPackageRepository packages;
    private final TaskPackageConfirmationRepository confirmations;
    private final WorkflowService workflowService;
    private final WorkflowRepository workflows;
    private final ProjectRepository projects;
    private final WorkflowStateMachine stateMachine;
    private final FinalReportValidator reports;
    private final OutboxJobRepository outboxJobs;
    private final GitOperationRepository gitOperations;

    public TaskDeliveryService(TaskRepository tasks, TaskDeliveryRepository deliveries,
                               TaskAssignmentRepository assignments, TaskPackageRepository packages,
                               TaskPackageConfirmationRepository confirmations, WorkflowService workflowService,
                               WorkflowRepository workflows, ProjectRepository projects,
                               WorkflowStateMachine stateMachine, FinalReportValidator reports,
                               OutboxJobRepository outboxJobs, GitOperationRepository gitOperations) {
        this.tasks = tasks; this.deliveries = deliveries; this.assignments = assignments;
        this.packages = packages; this.confirmations = confirmations; this.workflowService = workflowService;
        this.workflows = workflows; this.projects = projects; this.stateMachine = stateMachine;
        this.reports = reports; this.outboxJobs = outboxJobs; this.gitOperations = gitOperations;
    }

    @Transactional
    public TaskDeliveryDtos.DeliveryResponse submit(Long actorId, Long taskId,
                                                    TaskDeliveryDtos.SubmitRequest request) {
        Task task = tasks.findByIdForUpdate(taskId)
                .orElseThrow(() -> notFound("TASK_NOT_FOUND", "Task 不存在"));
        Workflow workflow = workflowService.requireForUpdate(actorId, task.getWorkflowId());
        workflowService.requireActiveProject(workflow);
        Project project = projects.findById(workflow.getProjectId())
                .orElseThrow(() -> notFound("PROJECT_NOT_FOUND", "项目不存在"));
        requireDeliveryEnabled(workflow, project);
        requireAssignee(taskId, actorId);

        TaskPackage current = packages.findByTaskIdAndStatus(taskId, TaskPackageStatus.CURRENT)
                .orElseThrow(() -> notFound("TASK_PACKAGE_NOT_FOUND", "当前任务包不存在"));
        requireCurrentPackage(task, current, request);
        requirePackageContext(current);
        requireLatestConfirmation(taskId, actorId, current);
        validateReport(task, current, request);

        String commitSha = request.commitSha().toLowerCase();
        var existing = deliveries.findByTaskIdAndCommitSha(taskId, commitSha);
        if (existing.isPresent()) {
            TaskDelivery delivery = existing.orElseThrow();
            if (sameSubmission(delivery, actorId, request)) return TaskDeliveryDtos.DeliveryResponse.from(delivery);
            throw conflict("DELIVERY_COMMIT_ALREADY_SUBMITTED", "当前 Commit 已存在不同的交付记录");
        }
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw conflict("TASK_STATE_CONFLICT", "当前 Task 状态不允许提交交付");
        }

        if (project.getDefaultBranch().equals(request.branchName())) {
            throw conflict("DEFAULT_BRANCH_DELIVERY_FORBIDDEN", "不能使用项目默认分支提交任务交付");
        }
        if (!Objects.equals(task.getBranchName(), request.branchName())) {
            throw conflict("TASK_BRANCH_MISMATCH", "交付分支与任务分支不一致");
        }

        TaskDelivery delivery = deliveries.save(new TaskDelivery(taskId, actorId, current.getId(),
                current.getPackageVersion(), current.getCodeContextVersionId(), current.getContextPlanId(),
                current.getBaseCommit(), request.finalReport(), request.branchName(), commitSha,
                request.pullRequestUrl()));
        gitOperations.save(new GitOperation(workflow.getProjectId(), workflow.getId(), taskId,
                delivery.getId(), request.branchName(), commitSha, request.pullRequestUrl()));
        outboxJobs.save(new OutboxJob(OutboxJobType.GIT_SYNC, delivery.getId()));
        task.submitDelivery();
        tasks.save(task);
        if (workflow.getStatus() == WorkflowStatus.IN_PROGRESS) {
            stateMachine.transition(workflow, WorkflowStatus.DELIVERY_SUBMITTED);
            workflows.save(workflow);
        }
        return TaskDeliveryDtos.DeliveryResponse.from(delivery);
    }

    @Transactional(readOnly = true)
    public List<TaskDeliveryDtos.DeliveryResponse> list(Long actorId, Long taskId) {
        Task task = tasks.findById(taskId).orElseThrow(() -> notFound("TASK_NOT_FOUND", "Task 不存在"));
        workflowService.get(actorId, task.getWorkflowId());
        return deliveries.findByTaskIdOrderBySubmittedAtDesc(taskId).stream()
                .map(TaskDeliveryDtos.DeliveryResponse::from).toList();
    }

    private void requireAssignee(Long taskId, Long actorId) {
        TaskAssignment assignment = assignments.findCurrentForUpdate(taskId)
                .orElseThrow(() -> conflict("TASK_NOT_ASSIGNED", "Task 尚未分配负责人"));
        if (!assignment.getAssigneeUserId().equals(actorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_ASSIGNEE_REQUIRED", "只有当前负责人可以提交交付");
        }
    }

    private void requireCurrentPackage(Task task, TaskPackage current, TaskDeliveryDtos.SubmitRequest request) {
        if (!current.getId().equals(request.packageId())
                || current.getPackageVersion() != request.packageVersion()
                || !current.getContentHash().equals(request.packageHash())
                || !Integer.valueOf(current.getPackageVersion()).equals(task.getCurrentPackageVersion())) {
            throw stale(task.getId(), current.getPackageVersion(), request.packageVersion());
        }
    }

    private void requireLatestConfirmation(Long taskId, Long actorId, TaskPackage current) {
        TaskPackageConfirmation confirmation = confirmations
                .findTopByTaskIdAndUserIdOrderByCreatedAtDesc(taskId, actorId)
                .orElseThrow(() -> conflict("TASK_PACKAGE_NOT_CONFIRMED", "提交交付前必须确认当前任务包"));
        if (!confirmation.getPackageId().equals(current.getId())
                || confirmation.getPackageVersion() != current.getPackageVersion()
                || !confirmation.getContentHash().equals(current.getContentHash())) {
            throw stale(taskId, current.getPackageVersion(), confirmation.getPackageVersion());
        }
    }

    private void requirePackageContext(TaskPackage current) {
        if (current.getCodeContextVersionId() == null || current.getContextPlanId() == null
                || current.getBaseCommit() == null || current.getBaseCommit().isBlank()) {
            throw conflict("TASK_PACKAGE_CONTEXT_STALE", "当前任务包缺少可追溯 Code Context，请重新生成并确认");
        }
    }

    private void validateReport(Task task, TaskPackage current, TaskDeliveryDtos.SubmitRequest request) {
        try {
            reports.validate(request.finalReport());
        } catch (FinalReportValidationException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FINAL_REPORT", ex.getMessage());
        }
        JsonNode report = request.finalReport();
        JsonNode git = report.path("git");
        if (!task.getExternalKey().equals(report.path("taskId").asText())
                || current.getId() != report.path("packageId").asLong()
                || current.getPackageVersion() != report.path("packageVersion").asInt()
                || !current.getContentHash().equals(report.path("packageHash").asText())) {
            throw stale(task.getId(), current.getPackageVersion(), report.path("packageVersion").asInt());
        }
        if (!"READY_FOR_REVIEW".equals(report.path("outcome").asText())) {
            throw conflict("FINAL_REPORT_NOT_READY", "提交交付需要 READY_FOR_REVIEW Final Report");
        }
        if (report.has("codeContextVersionId")
                && (current.getCodeContextVersionId() != report.path("codeContextVersionId").asLong()
                || current.getContextPlanId() != report.path("contextPlanId").asLong()
                || !current.getBaseCommit().equalsIgnoreCase(report.path("baseCommitSha").asText()))) {
            throw conflict("FINAL_REPORT_CONTEXT_MISMATCH", "Final Report 与当前任务包的 Code Context 不一致");
        }
        if (!request.branchName().equals(git.path("branchName").asText())
                || !request.commitSha().equalsIgnoreCase(git.path("commitSha").asText())
                || !Objects.equals(request.pullRequestUrl(), nullableText(git.get("pullRequestUrl")))) {
            throw conflict("FINAL_REPORT_GIT_MISMATCH", "Final Report 与提交的 Git 信息不一致");
        }
        report.path("changedFiles").forEach(path -> requireNonSensitivePath(path.asText()));
    }

    private void requireNonSensitivePath(String path) {
        String normalized = path.replace('\\', '/').toLowerCase();
        if (normalized.equals(".env") || normalized.startsWith(".env.")
                || normalized.startsWith("secrets/") || normalized.endsWith(".pem")
                || normalized.endsWith(".key")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SENSITIVE_PATH_IN_REPORT",
                    "Final Report 不能包含敏感文件路径");
        }
    }

    private void requireDeliveryEnabled(Workflow workflow, Project project) {
        if (workflow.getCompletionMode() == WorkflowCompletionMode.CI_REQUIRED
                && project.getCiStatus() != ProjectCiStatus.CI_REQUIRED) {
            throw conflict("CI_BOOTSTRAP_REQUIRED", "项目必须先完成 CI Bootstrap 才能提交普通开发交付");
        }
    }

    private boolean sameSubmission(TaskDelivery delivery, Long actorId, TaskDeliveryDtos.SubmitRequest request) {
        return delivery.getSubmittedBy().equals(actorId)
                && delivery.getPackageId().equals(request.packageId())
                && delivery.getPackageVersion() == request.packageVersion()
                && delivery.getBranchName().equals(request.branchName())
                && delivery.getReportJson().equals(request.finalReport())
                && Objects.equals(delivery.getPullRequestUrl(), request.pullRequestUrl());
    }

    private String nullableText(JsonNode value) {
        return value == null || value.isNull() ? null : value.asText();
    }

    private ApiException stale(Long taskId, int currentVersion, int submittedVersion) {
        return new ApiException(HttpStatus.CONFLICT, "TASK_PACKAGE_STALE", "交付引用的任务包不是当前确认版本",
                Map.of("currentVersion", currentVersion, "submittedVersion", submittedVersion,
                        "latestPackageUrl", "/api/tasks/" + taskId + "/packages/current",
                        "diffUrl", "/api/tasks/" + taskId + "/packages/diff?from="
                                + submittedVersion + "&to=" + currentVersion));
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
