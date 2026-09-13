package com.example.agentcollab.service;

import com.example.agentcollab.client.CiProviderClient;
import com.example.agentcollab.domain.*;
import com.example.agentcollab.dto.DeliveryEvidenceDtos;
import com.example.agentcollab.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.example.agentcollab.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class CiSyncService {
    private final OutboxJobRepository jobs;
    private final CiRunRepository runs;
    private final TaskDeliveryRepository deliveries;
    private final TaskRepository tasks;
    private final WorkflowRepository workflows;
    private final ProjectRepository projects;
    private final ProjectAccessService access;
    private final TaskAssignmentRepository assignments;
    private final WorkflowStateMachine stateMachine;
    private final int maxAttempts;
    private final long retryDelayMs;
    private final long pollDelayMs;
    private final long lockTimeoutMs;

    public CiSyncService(OutboxJobRepository jobs, CiRunRepository runs,
                         TaskDeliveryRepository deliveries, TaskRepository tasks,
                         WorkflowRepository workflows, ProjectRepository projects,
                         ProjectAccessService access, TaskAssignmentRepository assignments,
                         WorkflowStateMachine stateMachine,
                         @Value("${app.ci.worker.max-attempts:3}") int maxAttempts,
                         @Value("${app.ci.worker.retry-delay-ms:1000}") long retryDelayMs,
                         @Value("${app.ci.worker.poll-delay-ms:5000}") long pollDelayMs,
                         @Value("${app.ci.worker.lock-timeout-ms:300000}") long lockTimeoutMs) {
        this.jobs = jobs;
        this.runs = runs;
        this.deliveries = deliveries;
        this.tasks = tasks;
        this.workflows = workflows;
        this.projects = projects;
        this.access = access;
        this.assignments = assignments;
        this.stateMachine = stateMachine;
        this.maxAttempts = maxAttempts;
        this.retryDelayMs = retryDelayMs;
        this.pollDelayMs = pollDelayMs;
        this.lockTimeoutMs = lockTimeoutMs;
    }

    @Transactional
    public Optional<ClaimedCiJob> claimNext() {
        Optional<OutboxJob> candidate = jobs.findNextDueForUpdate(OutboxJobType.CI_SYNC, Instant.now());
        if (candidate.isEmpty()) return Optional.empty();
        OutboxJob job = candidate.get();
        CiRun run = runs.findByIdForUpdate(job.getReferenceId()).orElse(null);
        if (run == null || run.isTerminal()) {
            job.fail("CIRun is missing or inactive");
            return Optional.empty();
        }
        job.claim();
        return Optional.of(new ClaimedCiJob(job.getId(), run.getId()));
    }

    @Transactional(readOnly = true)
    public CiSyncContext context(ClaimedCiJob claimed) {
        CiRun run = runs.findById(claimed.runId()).orElseThrow();
        Project project = projects.findById(run.getProjectId()).orElseThrow();
        return new CiSyncContext(project, run);
    }

    @Transactional
    public DeliveryEvidenceDtos.CiRunResponse retry(Long actorId, Long taskId, Long runId) {
        Task task = tasks.findByIdForUpdate(taskId)
                .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task 不存在"));
        CiRun run = runs.findByIdForUpdate(runId)
                .filter(value -> value.getTaskId().equals(taskId))
                .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "CI_RUN_NOT_FOUND", "CI Run 不存在"));
        Workflow workflow = workflows.findById(run.getWorkflowId())
                .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "WORKFLOW_NOT_FOUND", "Workflow 不存在"));
        ProjectMember member = access.requireMember(workflow.getProjectId(), actorId);
        boolean assignee = assignments.findByTaskIdAndCurrentTrue(taskId)
                .map(value -> value.getAssigneeUserId().equals(actorId)).orElse(false);
        if (!assignee && member.getProjectRole() != ProjectMember.Role.LEADER) {
            throw api(HttpStatus.FORBIDDEN, "CI_RETRY_FORBIDDEN", "只有当前任务负责人或项目 Leader 可以重新同步 CI");
        }
        if (run.getStatus() != CiRunStatus.UNKNOWN) {
            throw api(HttpStatus.CONFLICT, "CI_RUN_NOT_RETRYABLE", "只有状态为 UNKNOWN 的 CI Run 可以重新同步");
        }
        TaskDelivery delivery = deliveries.findById(run.getDeliveryId()).orElse(null);
        if (delivery == null || delivery.getStatus() != TaskDeliveryStatus.CI_RUNNING
                || task.getStatus() != TaskStatus.CI_RUNNING) {
            throw api(HttpStatus.CONFLICT, "CI_DELIVERY_NOT_ACTIVE", "当前交付已不处于 CI 校验阶段");
        }
        OutboxJob job = jobs.findByReferenceForUpdate(OutboxJobType.CI_SYNC, runId)
                .orElseThrow(() -> api(HttpStatus.CONFLICT, "CI_SYNC_JOB_NOT_FOUND", "CI 同步任务不存在"));
        if (job.getStatus() == OutboxJobStatus.FAILED) {
            job.restart(Instant.now());
            run.pending("等待重新同步 CI", run.getDetailsUrl());
        } else if (!job.isActive()) {
            throw api(HttpStatus.CONFLICT, "CI_SYNC_JOB_NOT_RETRYABLE", "CI 同步任务当前不能重新执行");
        }
        return DeliveryEvidenceDtos.CiRunResponse.from(run);
    }

    /** Re-checks completion for workflows that may have crossed the task/CI finish order. */
    @Transactional
    public void reconcileWorkflow(Long actorId, Long workflowId) {
        Workflow workflow = workflows.findByIdForUpdate(workflowId)
                .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "WORKFLOW_NOT_FOUND", "Workflow 不存在"));
        access.requireMember(workflow.getProjectId(), actorId);
        tryAdvanceBootstrapWorkflow(workflow);
    }

    @Transactional
    public void complete(ClaimedCiJob claimed, CiProviderClient.CiProviderResult result) {
        OutboxJob job = jobs.findByIdForUpdate(claimed.jobId()).orElseThrow();
        CiRun run = runs.findByIdForUpdate(claimed.runId()).orElseThrow();
        if (job.getStatus() != OutboxJobStatus.RUNNING || run.isTerminal()) return;

        TaskDelivery delivery = deliveries.findById(run.getDeliveryId()).orElse(null);
        if (delivery == null || delivery.getStatus() != TaskDeliveryStatus.CI_RUNNING
                || !delivery.getCommitSha().equalsIgnoreCase(run.getCommitSha())) {
            recordUnknown(run, job, "CI run SHA does not match the current delivery");
            return;
        }
        Workflow workflow = workflows.findByIdForUpdate(run.getWorkflowId()).orElseThrow();
        if (result.headSha() == null || result.headSha().isBlank()) {
            if (result.status() == CiRunStatus.UNKNOWN) {
                if (workflow.getCompletionMode() == WorkflowCompletionMode.CI_BOOTSTRAP
                        && isBootstrapPreparationDelivery(delivery)) {
                    completeBootstrapPreparation(run, delivery, workflow, job);
                    return;
                }
                recordUnknown(run, job, "当前 Commit 尚无 CI 检查");
            } else {
                recordUnknown(run, job, "CI Provider 未返回 head SHA");
            }
            return;
        }
        if (!run.getCommitSha().equalsIgnoreCase(result.headSha())) {
            recordUnknown(run, job, "CI head SHA 不匹配（请检查当前 Commit 的 CI 检查结果）");
            return;
        }
        run.identify(result.externalId());

        switch (result.status()) {
            case PENDING -> {
                run.pending(result.conclusion(), result.detailsUrl());
                job.pollAgainAt(Instant.now().plus(pollDelayMs, ChronoUnit.MILLIS));
            }
            case RUNNING -> {
                run.running(result.conclusion(), result.detailsUrl());
                job.pollAgainAt(Instant.now().plus(pollDelayMs, ChronoUnit.MILLIS));
            }
            case UNKNOWN -> {
                run.unknown(result.conclusion(), result.detailsUrl());
                if (job.getAttemptCount() < maxAttempts) {
                    job.retryAt(Instant.now().plus(pollDelayMs, ChronoUnit.MILLIS), result.conclusion());
                } else {
                    job.fail(result.conclusion());
                }
            }
            case PASSED -> completePassed(run, delivery, workflow, job, result);
            case FAILED -> completeFailed(run, delivery, job, result);
        }
    }

    @Transactional
    public void handleFailure(ClaimedCiJob claimed, String message, boolean retryable) {
        OutboxJob job = jobs.findByIdForUpdate(claimed.jobId()).orElseThrow();
        CiRun run = runs.findByIdForUpdate(claimed.runId()).orElseThrow();
        if (job.getStatus() != OutboxJobStatus.RUNNING || run.isTerminal()) return;
        String safeMessage = safeMessage(message, "CI Provider synchronization failed");
        run.unknown(safeMessage, run.getDetailsUrl());
        if (retryable && job.getAttemptCount() < maxAttempts) {
            job.retryAt(Instant.now().plus(retryDelayMs, ChronoUnit.MILLIS), safeMessage);
        } else {
            job.fail(safeMessage);
        }
    }

    @Transactional
    public int recoverTimedOut() {
        Instant deadline = Instant.now().minus(lockTimeoutMs, ChronoUnit.MILLIS);
        var timedOut = jobs.findTimedOutForUpdate(
                OutboxJobType.CI_SYNC, OutboxJobStatus.RUNNING, deadline);
        for (OutboxJob job : timedOut) {
            CiRun run = runs.findByIdForUpdate(job.getReferenceId()).orElse(null);
            if (run == null || run.isTerminal()) {
                job.fail("CIRun is missing or inactive");
            } else if (job.getAttemptCount() < maxAttempts) {
                run.unknown("CI Provider synchronization timed out", run.getDetailsUrl());
                job.retryAt(Instant.now(), "CI Provider synchronization timed out");
            } else {
                run.unknown("CI Provider synchronization timed out", run.getDetailsUrl());
                job.fail("CI Provider synchronization timed out");
            }
        }
        return timedOut.size();
    }

    private void completePassed(CiRun run, TaskDelivery delivery, Workflow workflow, OutboxJob job,
                                CiProviderClient.CiProviderResult result) {
        if (workflow.getCompletionMode() == WorkflowCompletionMode.CI_BOOTSTRAP
                && (!result.configurationPresent() || !result.configurationRecognized())) {
            completeFailed(run, delivery, job, new CiProviderClient.CiProviderResult(
                    result.externalId(), result.headSha(), CiRunStatus.FAILED,
                    "CI bootstrap configuration is missing or not recognized", result.detailsUrl(),
                    result.configurationPresent(), result.configurationRecognized()));
            return;
        }

        Task task = tasks.findByIdForUpdate(run.getTaskId()).orElseThrow();
        run.passed(result.conclusion(), result.detailsUrl(),
                result.configurationPresent(), result.configurationRecognized());
        delivery.markPassed();
        task.completeFromCi();
        tryAdvanceBootstrapWorkflow(workflow);
        job.succeed();
    }

    private void completeFailed(CiRun run, TaskDelivery delivery, OutboxJob job,
                                CiProviderClient.CiProviderResult result) {
        Task task = tasks.findByIdForUpdate(run.getTaskId()).orElseThrow();
        run.failed(result.conclusion(), result.detailsUrl(),
                result.configurationPresent(), result.configurationRecognized());
        delivery.markFailed("Current Commit CI did not pass");
        task.returnForCiRework();
        job.succeed();
    }

    /**
     * The first Bootstrap tasks establish the project files before a workflow exists.
     * They are allowed to complete without a CI check; the task that adds
     * .github/workflows remains subject to the real CI gate.
     */
    private void completeBootstrapPreparation(CiRun run, TaskDelivery delivery,
                                               Workflow workflow, OutboxJob job) {
        Task task = tasks.findByIdForUpdate(run.getTaskId()).orElseThrow();
        run.unknown("Bootstrap 前置任务：CI 尚未建立，无需检查", run.getDetailsUrl());
        delivery.markPassed();
        task.completeFromCi();
        tryAdvanceBootstrapWorkflow(workflow);
        job.succeed();
    }

    private void tryAdvanceBootstrapWorkflow(Workflow workflow) {
        if (workflow.getStatus() != WorkflowStatus.CI_RUNNING) return;
        boolean allTasksDone = tasks.findByWorkflowIdOrderById(workflow.getId()).stream()
                .allMatch(value -> value.getStatus() == TaskStatus.DONE);
        if (!allTasksDone) return;
        boolean bootstrapCiPassed = runs.findByWorkflowId(workflow.getId()).stream()
                .anyMatch(run -> run.getStatus() == CiRunStatus.PASSED
                        && Boolean.TRUE.equals(run.getConfigurationPresent())
                        && Boolean.TRUE.equals(run.getConfigurationRecognized()));
        if (!bootstrapCiPassed) return;
        stateMachine.transition(workflow, WorkflowStatus.CI_PASSED);
        stateMachine.transition(workflow, WorkflowStatus.READY_TO_CLOSE);
    }

    private boolean isBootstrapPreparationDelivery(TaskDelivery delivery) {
        JsonNode changedFiles = delivery.getReportJson().path("changedFiles");
        if (!changedFiles.isArray()) return false;
        boolean workflowFileChanged = false;
        for (JsonNode file : changedFiles) {
            String path = file.asText("").replace('\\', '/').toLowerCase();
            if (path.startsWith(".github/workflows/") || path.contains("/.github/workflows/")) {
                workflowFileChanged = true;
                break;
            }
        }
        return !workflowFileChanged;
    }

    private void recordUnknown(CiRun run, OutboxJob job, String message) {
        run.unknown(message, run.getDetailsUrl());
        if (job.getAttemptCount() < maxAttempts) {
            job.retryAt(Instant.now().plus(retryDelayMs, ChronoUnit.MILLIS), message);
        } else {
            job.fail(message);
        }
    }

    private String safeMessage(String value, String fallback) {
        String message = value == null || value.isBlank() ? fallback : value;
        message = message.replace('\r', ' ').replace('\n', ' ');
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private ApiException api(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }

    public record ClaimedCiJob(Long jobId, Long runId) {}
    public record CiSyncContext(Project project, CiRun run) {}
}
