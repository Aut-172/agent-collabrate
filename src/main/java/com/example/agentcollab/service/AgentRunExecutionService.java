package com.example.agentcollab.service;

import com.example.agentcollab.client.AgentProviderResult;
import com.example.agentcollab.domain.*;
import com.example.agentcollab.repository.AgentRunRepository;
import com.example.agentcollab.repository.OutboxJobRepository;
import com.example.agentcollab.repository.WorkflowRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class AgentRunExecutionService {
    private final OutboxJobRepository jobs;
    private final AgentRunRepository runs;
    private final DocumentService documents;
    private final WorkflowRepository workflows;
    private final CodeContextOrchestrator codeContext;
    private final int maxAttempts;
    private final long retryDelayMs;
    private final long lockTimeoutMs;

    public AgentRunExecutionService(OutboxJobRepository jobs, AgentRunRepository runs,
                                    DocumentService documents, WorkflowRepository workflows,
                                    CodeContextOrchestrator codeContext,
                                    @Value("${app.agent.worker.max-attempts:2}") int maxAttempts,
                                    @Value("${app.agent.worker.retry-delay-ms:1000}") long retryDelayMs,
                                    @Value("${app.agent.worker.lock-timeout-ms:300000}") long lockTimeoutMs) {
        this.jobs = jobs;
        this.runs = runs;
        this.documents = documents;
        this.workflows = workflows;
        this.codeContext = codeContext;
        this.maxAttempts = maxAttempts;
        this.retryDelayMs = retryDelayMs;
        this.lockTimeoutMs = lockTimeoutMs;
    }

    @Transactional
    public Optional<ClaimedAgentJob> claimNext() {
        Optional<OutboxJob> candidate = jobs.findNextDueForUpdate(Instant.now());
        if (candidate.isEmpty()) return Optional.empty();
        OutboxJob job = candidate.get();
        AgentRun run = runs.findByIdForUpdate(job.getReferenceId()).orElse(null);
        if (run == null || !run.isActive()) {
            job.fail("AgentRun is missing or inactive");
            return Optional.empty();
        }
        job.claim();
        if (run.getStatus() == AgentRunStatus.QUEUED) run.start();
        return Optional.of(new ClaimedAgentJob(job.getId(), run.getId()));
    }

    @Transactional
    public void complete(ClaimedAgentJob claimed, AgentProviderResult result) {
        AgentRun visibleRun = runs.findById(claimed.runId()).orElseThrow();
        workflows.findByIdForUpdate(visibleRun.getWorkflowId()).orElseThrow();
        OutboxJob job = jobs.findByIdForUpdate(claimed.jobId()).orElseThrow();
        AgentRun run = runs.findByIdForUpdate(claimed.runId()).orElseThrow();
        if (job.getStatus() != OutboxJobStatus.RUNNING || run.getStatus() != AgentRunStatus.RUNNING) return;

        switch (run.getRunType()) {
            case GENERATE_CODE_CONTEXT_PLAN -> codeContext.recordPlan(run.getId(), result.content());
            case GENERATE_DESIGN -> documents.recordGeneratedDesign(run.getWorkflowId(), result.content(), run.getId());
            case GENERATE_SPEC -> documents.recordGeneratedSpec(run.getWorkflowId(), result.content(), run.getId());
            case GENERATE_BUILD_PLAN -> documents.recordGeneratedBuildPlan(
                    run.getWorkflowId(), result.content(), run.getId());
        }
        run.succeed(safeSummary(result.summary()));
        job.succeed();
    }

    @Transactional
    public void handleFailure(ClaimedAgentJob claimed, String code, String message, boolean retryable) {
        OutboxJob job = jobs.findByIdForUpdate(claimed.jobId()).orElseThrow();
        AgentRun run = runs.findByIdForUpdate(claimed.runId()).orElseThrow();
        if (job.getStatus() != OutboxJobStatus.RUNNING || run.getStatus() != AgentRunStatus.RUNNING) return;
        String safeCode = safeCode(code);
        String safeMessage = safeMessage(message);
        if (retryable && job.getAttemptCount() < maxAttempts) {
            run.recordRetry(safeCode, safeMessage);
            job.retryAt(Instant.now().plus(retryDelayMs, ChronoUnit.MILLIS), safeMessage);
        } else {
            run.fail(safeCode, safeMessage);
            job.fail(safeMessage);
        }
    }

    @Transactional
    public int recoverTimedOut() {
        Instant deadline = Instant.now().minus(lockTimeoutMs, ChronoUnit.MILLIS);
        var timedOut = jobs.findTimedOutForUpdate(
                OutboxJobType.AGENT_RUN, OutboxJobStatus.RUNNING, deadline);
        for (OutboxJob job : timedOut) {
            AgentRun run = runs.findByIdForUpdate(job.getReferenceId()).orElse(null);
            if (run == null || run.getStatus() != AgentRunStatus.RUNNING) {
                job.fail("AgentRun is missing or inactive");
            } else if (job.getAttemptCount() < maxAttempts) {
                run.recordRetry("WORKER_TIMEOUT", "Worker execution timed out");
                job.retryAt(Instant.now(), "Worker execution timed out");
            } else {
                run.fail("WORKER_TIMEOUT", "Worker execution timed out");
                job.fail("Worker execution timed out");
            }
        }
        return timedOut.size();
    }

    private String safeSummary(String value) {
        return value == null || value.isBlank() ? "Agent output persisted" : sanitize(value, 500);
    }

    private String safeCode(String value) {
        return value == null || value.isBlank() ? "AGENT_EXECUTION_FAILED" : sanitize(value, 100);
    }

    private String safeMessage(String value) {
        return value == null || value.isBlank() ? "Agent execution failed" : sanitize(value, 500);
    }

    private String sanitize(String value, int maxLength) {
        String sanitized = value.replace('\r', ' ').replace('\n', ' ');
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
    }

    public record ClaimedAgentJob(Long jobId, Long runId) {}
}
