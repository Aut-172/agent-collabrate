package com.example.agentcollab.service;

import com.example.agentcollab.client.GitProviderClient;
import com.example.agentcollab.domain.*;
import com.example.agentcollab.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class GitSyncService {
    private final OutboxJobRepository jobs;
    private final GitOperationRepository operations;
    private final TaskDeliveryRepository deliveries;
    private final TaskRepository tasks;
    private final WorkflowRepository workflows;
    private final ProjectRepository projects;
    private final CiRunRepository ciRuns;
    private final WorkflowStateMachine stateMachine;
    private final int maxAttempts;
    private final long retryDelayMs;
    private final long lockTimeoutMs;

    public GitSyncService(OutboxJobRepository jobs, GitOperationRepository operations,
                          TaskDeliveryRepository deliveries, TaskRepository tasks,
                          WorkflowRepository workflows, ProjectRepository projects,
                          CiRunRepository ciRuns, WorkflowStateMachine stateMachine,
                          @Value("${app.git.worker.max-attempts:3}") int maxAttempts,
                          @Value("${app.git.worker.retry-delay-ms:1000}") long retryDelayMs,
                          @Value("${app.git.worker.lock-timeout-ms:300000}") long lockTimeoutMs) {
        this.jobs = jobs;
        this.operations = operations;
        this.deliveries = deliveries;
        this.tasks = tasks;
        this.workflows = workflows;
        this.projects = projects;
        this.ciRuns = ciRuns;
        this.stateMachine = stateMachine;
        this.maxAttempts = maxAttempts;
        this.retryDelayMs = retryDelayMs;
        this.lockTimeoutMs = lockTimeoutMs;
    }

    @Transactional
    public Optional<ClaimedGitJob> claimNext() {
        Optional<OutboxJob> candidate = jobs.findNextDueForUpdate(OutboxJobType.GIT_SYNC, Instant.now());
        if (candidate.isEmpty()) return Optional.empty();
        OutboxJob job = candidate.get();
        GitOperation operation = operations.findByDeliveryId(job.getReferenceId()).orElse(null);
        if (operation == null || operation.getStatus() != GitOperationStatus.PENDING) {
            job.fail("GitOperation is missing or inactive");
            return Optional.empty();
        }
        job.claim();
        return Optional.of(new ClaimedGitJob(job.getId(), operation.getId()));
    }

    @Transactional(readOnly = true)
    public GitSyncContext context(ClaimedGitJob claimed) {
        GitOperation operation = operations.findById(claimed.operationId()).orElseThrow();
        Project project = projects.findById(operation.getProjectId()).orElseThrow();
        TaskDelivery delivery = deliveries.findById(operation.getDeliveryId()).orElseThrow();
        return new GitSyncContext(project, delivery);
    }

    @Transactional
    public void complete(ClaimedGitJob claimed, GitProviderClient.GitValidationResult result) {
        OutboxJob job = jobs.findByIdForUpdate(claimed.jobId()).orElseThrow();
        GitOperation operation = operations.findByIdForUpdate(claimed.operationId()).orElseThrow();
        if (job.getStatus() != OutboxJobStatus.RUNNING
                || operation.getStatus() != GitOperationStatus.PENDING) return;

        TaskDelivery delivery = deliveries.findById(operation.getDeliveryId()).orElse(null);
        if (delivery == null || delivery.getStatus() != TaskDeliveryStatus.SUBMITTED
                || !delivery.getCommitSha().equalsIgnoreCase(operation.getCommitSha())) {
            reject(operation, delivery, job, "Git operation does not match the current submitted delivery");
            return;
        }
        if (!result.isValid()) {
            reject(operation, delivery, job, safeMessage(result.errorMessage(), "Git facts do not match delivery"));
            return;
        }

        Project project = projects.findById(operation.getProjectId()).orElseThrow();
        Task task = tasks.findByIdForUpdate(operation.getTaskId()).orElseThrow();
        Workflow workflow = workflows.findByIdForUpdate(operation.getWorkflowId()).orElseThrow();
        operation.succeed(result.externalId());
        CiRun run = ciRuns.findByDeliveryIdAndCommitSha(delivery.getId(), delivery.getCommitSha())
                .orElseGet(() -> ciRuns.save(new CiRun(project.getId(), workflow.getId(), task.getId(),
                        delivery.getId(), delivery.getCommitSha())));
        delivery.markCiRunning();
        task.markCiRunning();
        if (workflow.getStatus() == WorkflowStatus.DELIVERY_SUBMITTED) {
            stateMachine.transition(workflow, WorkflowStatus.CI_RUNNING);
        }
        jobs.save(new OutboxJob(OutboxJobType.CI_SYNC, run.getId()));
        job.succeed();
    }

    @Transactional
    public void handleFailure(ClaimedGitJob claimed, String message, boolean retryable) {
        OutboxJob job = jobs.findByIdForUpdate(claimed.jobId()).orElseThrow();
        GitOperation operation = operations.findByIdForUpdate(claimed.operationId()).orElseThrow();
        if (job.getStatus() != OutboxJobStatus.RUNNING
                || operation.getStatus() != GitOperationStatus.PENDING) return;
        String safeMessage = safeMessage(message, "Git Provider synchronization failed");
        if (retryable && job.getAttemptCount() < maxAttempts) {
            job.retryAt(Instant.now().plus(retryDelayMs, ChronoUnit.MILLIS), safeMessage);
        } else {
            operation.fail(safeMessage);
            job.fail(safeMessage);
        }
    }

    @Transactional
    public int recoverTimedOut() {
        Instant deadline = Instant.now().minus(lockTimeoutMs, ChronoUnit.MILLIS);
        var timedOut = jobs.findTimedOutForUpdate(
                OutboxJobType.GIT_SYNC, OutboxJobStatus.RUNNING, deadline);
        for (OutboxJob job : timedOut) {
            GitOperation operation = operations.findByDeliveryId(job.getReferenceId()).orElse(null);
            if (operation == null || operation.getStatus() != GitOperationStatus.PENDING) {
                job.fail("GitOperation is missing or inactive");
            } else if (job.getAttemptCount() < maxAttempts) {
                job.retryAt(Instant.now(), "Git Provider synchronization timed out");
            } else {
                operation.fail("Git Provider synchronization timed out");
                job.fail("Git Provider synchronization timed out");
            }
        }
        return timedOut.size();
    }

    private void reject(GitOperation operation, TaskDelivery delivery, OutboxJob job, String message) {
        operation.fail(message);
        if (delivery != null && delivery.getStatus() == TaskDeliveryStatus.SUBMITTED) delivery.reject(message);
        Task task = tasks.findByIdForUpdate(operation.getTaskId()).orElse(null);
        if (task != null && task.getStatus() == TaskStatus.DELIVERY_SUBMITTED) task.returnForDeliveryRework();
        job.fail(message);
    }

    private String safeMessage(String value, String fallback) {
        String message = value == null || value.isBlank() ? fallback : value;
        message = message.replace('\r', ' ').replace('\n', ' ');
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    public record ClaimedGitJob(Long jobId, Long operationId) {}
    public record GitSyncContext(Project project, TaskDelivery delivery) {}
}
