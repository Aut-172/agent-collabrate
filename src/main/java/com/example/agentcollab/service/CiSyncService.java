package com.example.agentcollab.service;

import com.example.agentcollab.client.CiProviderClient;
import com.example.agentcollab.domain.*;
import com.example.agentcollab.repository.*;
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
    private final WorkflowStateMachine stateMachine;
    private final int maxAttempts;
    private final long retryDelayMs;
    private final long pollDelayMs;
    private final long lockTimeoutMs;

    public CiSyncService(OutboxJobRepository jobs, CiRunRepository runs,
                         TaskDeliveryRepository deliveries, TaskRepository tasks,
                         WorkflowRepository workflows, ProjectRepository projects,
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
        if (result.headSha() == null || !run.getCommitSha().equalsIgnoreCase(result.headSha())) {
            recordUnknown(run, job, "Provider returned a different head SHA");
            return;
        }
        run.identify(result.externalId());

        Workflow workflow = workflows.findByIdForUpdate(run.getWorkflowId()).orElseThrow();
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
                job.pollAgainAt(Instant.now().plus(pollDelayMs, ChronoUnit.MILLIS));
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
        boolean allTasksDone = tasks.findByWorkflowIdOrderById(workflow.getId()).stream()
                .allMatch(value -> value.getStatus() == TaskStatus.DONE);
        if (allTasksDone && workflow.getStatus() == WorkflowStatus.CI_RUNNING) {
            stateMachine.transition(workflow, WorkflowStatus.CI_PASSED);
            stateMachine.transition(workflow, WorkflowStatus.READY_TO_CLOSE);
        }
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

    public record ClaimedCiJob(Long jobId, Long runId) {}
    public record CiSyncContext(Project project, CiRun run) {}
}
