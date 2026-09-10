package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class RepoIngestionService {
    private final OutboxJobRepository jobs;
    private final CodeContextRunRepository runs;
    private final RepoInventoryVersionRepository inventories;
    private final RepoInventoryFileRepository files;
    private final ProjectRepository projects;
    private final CodeContextVersionRepository contexts;
    private final int maxAttempts;
    private final long retryDelayMs;
    private final long lockTimeoutMs;

    public RepoIngestionService(OutboxJobRepository jobs, CodeContextRunRepository runs,
                                RepoInventoryVersionRepository inventories,
                                RepoInventoryFileRepository files, ProjectRepository projects,
                                CodeContextVersionRepository contexts,
                                @Value("${app.code-context.worker.max-attempts:3}") int maxAttempts,
                                @Value("${app.code-context.worker.retry-delay-ms:1000}") long retryDelayMs,
                                @Value("${app.code-context.worker.lock-timeout-ms:300000}") long lockTimeoutMs) {
        this.jobs = jobs;
        this.runs = runs;
        this.inventories = inventories;
        this.files = files;
        this.projects = projects;
        this.contexts = contexts;
        this.maxAttempts = maxAttempts;
        this.retryDelayMs = retryDelayMs;
        this.lockTimeoutMs = lockTimeoutMs;
    }

    @Transactional
    public Optional<ClaimedIngestionJob> claimNext() {
        Optional<OutboxJob> candidate = jobs.findNextDueForUpdate(OutboxJobType.CODE_CONTEXT_SYNC, Instant.now());
        if (candidate.isEmpty()) return Optional.empty();
        OutboxJob job = candidate.get();
        CodeContextRun run = runs.findByIdForUpdate(job.getReferenceId()).orElse(null);
        if (run == null || !run.isActive()) {
            job.fail("CodeContextRun is missing or inactive");
            return Optional.empty();
        }
        job.claim();
        if (run.getStatus() == CodeContextRunStatus.QUEUED) run.start();
        return Optional.of(new ClaimedIngestionJob(job.getId(), run.getId()));
    }

    @Transactional(readOnly = true)
    public IngestionContext context(ClaimedIngestionJob claimed) {
        CodeContextRun run = runs.findById(claimed.runId()).orElseThrow();
        Project project = projects.findById(run.getProjectId()).orElseThrow();
        return new IngestionContext(project);
    }

    @Transactional
    public void complete(ClaimedIngestionJob claimed, RepoInventoryBuilder.InventorySnapshot snapshot) {
        OutboxJob job = jobs.findByIdForUpdate(claimed.jobId()).orElseThrow();
        CodeContextRun run = runs.findByIdForUpdate(claimed.runId()).orElseThrow();
        if (job.getStatus() != OutboxJobStatus.RUNNING || run.getStatus() != CodeContextRunStatus.RUNNING) return;
        Project project = projects.findByIdForUpdate(run.getProjectId()).orElseThrow();

        RepoInventoryVersion inventory = inventories
                .findByProjectIdAndProviderAndBranchNameAndCommitSha(project.getId(), "GIT",
                        snapshot.branchName(), snapshot.commitSha())
                .orElse(null);
        for (RepoInventoryVersion current :
                inventories.findByProjectIdAndStatus(project.getId(), RepoInventoryStatus.CURRENT)) {
            if (inventory == null || !current.getId().equals(inventory.getId())) current.markStale();
        }
        if (inventory == null) {
            inventory = inventories.save(new RepoInventoryVersion(project.getId(), project.getRepositoryUrl(),
                    snapshot.branchName(), snapshot.commitSha(), snapshot.repositoryProfile(), snapshot.treeSummary()));
            Long inventoryId = inventory.getId();
            files.saveAll(snapshot.files().stream().map(file -> new RepoInventoryFile(inventoryId, file.path(),
                    file.fileType(), file.sizeBytes(), file.contentHash(), file.indexedSummary())).toList());
        } else {
            inventory.markCurrent();
        }
        project.recordContextCommit(snapshot.commitSha());
        contexts.findByProjectIdAndStatus(project.getId(), CodeContextStatus.CURRENT).stream()
                .filter(context -> !context.getBaseCommitSha().equalsIgnoreCase(snapshot.commitSha()))
                .forEach(CodeContextVersion::markStale);
        run.succeed(inventory.getId());
        job.succeed();
    }

    @Transactional
    public void handleFailure(ClaimedIngestionJob claimed, String message, boolean retryable) {
        OutboxJob job = jobs.findByIdForUpdate(claimed.jobId()).orElseThrow();
        CodeContextRun run = runs.findByIdForUpdate(claimed.runId()).orElseThrow();
        if (job.getStatus() != OutboxJobStatus.RUNNING || run.getStatus() != CodeContextRunStatus.RUNNING) return;
        String safe = safeMessage(message);
        if (retryable && job.getAttemptCount() < maxAttempts) {
            job.retryAt(Instant.now().plus(retryDelayMs, ChronoUnit.MILLIS), safe);
        } else {
            run.fail(safe);
            job.fail(safe);
        }
    }

    @Transactional
    public int recoverTimedOut() {
        Instant deadline = Instant.now().minus(lockTimeoutMs, ChronoUnit.MILLIS);
        var timedOut = jobs.findTimedOutForUpdate(
                OutboxJobType.CODE_CONTEXT_SYNC, OutboxJobStatus.RUNNING, deadline);
        for (OutboxJob job : timedOut) {
            CodeContextRun run = runs.findByIdForUpdate(job.getReferenceId()).orElse(null);
            if (run == null || run.getStatus() != CodeContextRunStatus.RUNNING) {
                job.fail("CodeContextRun is missing or inactive");
            } else if (job.getAttemptCount() < maxAttempts) {
                job.retryAt(Instant.now(), "Repo ingestion timed out");
            } else {
                run.fail("Repo ingestion timed out");
                job.fail("Repo ingestion timed out");
            }
        }
        return timedOut.size();
    }

    private String safeMessage(String value) {
        String message = value == null || value.isBlank() ? "Code Context Provider synchronization failed" : value;
        message = message.replace('\r', ' ').replace('\n', ' ');
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    public record ClaimedIngestionJob(Long jobId, Long runId) {}
    public record IngestionContext(Project project) {}
}
