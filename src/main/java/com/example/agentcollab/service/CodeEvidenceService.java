package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class CodeEvidenceService {
    private final OutboxJobRepository jobs;
    private final CodeContextRunRepository runs;
    private final CodeContextPlanRepository plans;
    private final CodeContextVersionRepository contexts;
    private final CodeContextFileRepository evidenceFiles;
    private final RepoInventoryVersionRepository inventories;
    private final RepoInventoryFileRepository inventoryFiles;
    private final ProjectRepository projects;
    private final AgentRunRepository agentRuns;
    private final ObjectMapper json;
    private final int maxAttempts;
    private final long retryDelayMs;
    private final long lockTimeoutMs;

    public CodeEvidenceService(OutboxJobRepository jobs, CodeContextRunRepository runs,
                               CodeContextPlanRepository plans, CodeContextVersionRepository contexts,
                               CodeContextFileRepository evidenceFiles, RepoInventoryVersionRepository inventories,
                               RepoInventoryFileRepository inventoryFiles, ProjectRepository projects,
                               AgentRunRepository agentRuns, ObjectMapper json,
                               @Value("${app.code-context.worker.max-attempts:3}") int maxAttempts,
                               @Value("${app.code-context.worker.retry-delay-ms:1000}") long retryDelayMs,
                               @Value("${app.code-context.worker.lock-timeout-ms:300000}") long lockTimeoutMs) {
        this.jobs = jobs; this.runs = runs; this.plans = plans; this.contexts = contexts;
        this.evidenceFiles = evidenceFiles; this.inventories = inventories; this.inventoryFiles = inventoryFiles;
        this.projects = projects; this.agentRuns = agentRuns; this.json = json;
        this.maxAttempts = maxAttempts; this.retryDelayMs = retryDelayMs;
        this.lockTimeoutMs = lockTimeoutMs;
    }

    @Transactional
    public Optional<ClaimedEvidenceJob> claimNext() {
        Optional<OutboxJob> candidate = jobs.findNextDueForUpdate(OutboxJobType.CODE_CONTEXT_EVIDENCE, Instant.now());
        if (candidate.isEmpty()) return Optional.empty();
        OutboxJob job = candidate.get();
        CodeContextRun run = runs.findByIdForUpdate(job.getReferenceId()).orElse(null);
        if (run == null || run.getRunType() != CodeContextRun.Type.EVIDENCE_COLLECTION || !run.isActive()) {
            job.fail("Evidence CodeContextRun is missing or inactive"); return Optional.empty();
        }
        job.claim(); if (run.getStatus() == CodeContextRunStatus.QUEUED) run.start();
        return Optional.of(new ClaimedEvidenceJob(job.getId(), run.getId()));
    }

    @Transactional(readOnly = true)
    public CodeEvidenceCollector.EvidenceContext context(ClaimedEvidenceJob claimed) {
        CodeContextRun run = runs.findById(claimed.runId()).orElseThrow();
        CodeContextPlan plan = plans.findById(run.getContextPlanId()).orElseThrow();
        RepoInventoryVersion inventory = inventories.findById(plan.getInventoryVersionId()).orElseThrow();
        if (inventory.getStatus() != RepoInventoryStatus.CURRENT) {
            throw new com.example.agentcollab.client.ProviderSyncException("Repo Inventory became stale", false);
        }
        return new CodeEvidenceCollector.EvidenceContext(projects.findById(plan.getProjectId()).orElseThrow(),
                plan, inventory, inventoryFiles.findByInventoryVersionIdOrderByPath(inventory.getId()));
    }

    @Transactional
    public void complete(ClaimedEvidenceJob claimed, CodeEvidenceCollector.EvidenceBundle bundle) {
        OutboxJob job = jobs.findByIdForUpdate(claimed.jobId()).orElseThrow();
        CodeContextRun run = runs.findByIdForUpdate(claimed.runId()).orElseThrow();
        if (job.getStatus() != OutboxJobStatus.RUNNING || run.getStatus() != CodeContextRunStatus.RUNNING) return;
        CodeContextPlan plan = plans.findById(run.getContextPlanId()).orElseThrow();
        Project project = projects.findByIdForUpdate(plan.getProjectId()).orElseThrow();
        RepoInventoryVersion inventory = inventories.findById(plan.getInventoryVersionId()).orElseThrow();
        if (inventory.getStatus() != RepoInventoryStatus.CURRENT) {
            throw new com.example.agentcollab.client.ProviderSyncException("Repo Inventory became stale", false);
        }
        CodeContextVersion context = contexts.save(new CodeContextVersion(project.getId(), inventory.getId(),
                plan.getId(), project.getRepositoryUrl(), inventory.getBranchName(), inventory.getCommitSha(),
                inventory.getRepositoryProfile(), bundle.evidenceJson(), null));
        evidenceFiles.saveAll(bundle.files().stream().map(file -> new CodeContextFile(context.getId(), file.path(),
                file.contentHash(), file.evidenceType(), file.reason(), json.createArrayNode(), file.excerpt())).toList());
        plan.markUsed();
        AgentRun agentRun = agentRuns.findByIdForUpdate(plan.getAgentRunId()).orElseThrow();
        agentRun.bindCodeContext(plan.getId(), context.getId());
        run.succeedWithContext(context.getId()); job.succeed();
    }

    @Transactional
    public void handleFailure(ClaimedEvidenceJob claimed, String message, boolean retryable) {
        OutboxJob job = jobs.findByIdForUpdate(claimed.jobId()).orElseThrow();
        CodeContextRun run = runs.findByIdForUpdate(claimed.runId()).orElseThrow();
        if (job.getStatus() != OutboxJobStatus.RUNNING || run.getStatus() != CodeContextRunStatus.RUNNING) return;
        String safe = safeMessage(message);
        if (retryable && job.getAttemptCount() < maxAttempts) {
            job.retryAt(Instant.now().plus(retryDelayMs, ChronoUnit.MILLIS), safe);
        } else { run.fail(safe); job.fail(safe); }
    }

    @Transactional
    public int recoverTimedOut() {
        var timedOut = jobs.findTimedOutForUpdate(OutboxJobType.CODE_CONTEXT_EVIDENCE,
                OutboxJobStatus.RUNNING, Instant.now().minus(lockTimeoutMs, ChronoUnit.MILLIS));
        for (OutboxJob job : timedOut) {
            CodeContextRun run = runs.findByIdForUpdate(job.getReferenceId()).orElse(null);
            if (run == null || run.getStatus() != CodeContextRunStatus.RUNNING) {
                job.fail("Evidence CodeContextRun is missing or inactive");
            } else if (job.getAttemptCount() < maxAttempts) {
                job.retryAt(Instant.now(), "Code evidence collection timed out");
            } else {
                run.fail("Code evidence collection timed out"); job.fail("Code evidence collection timed out");
            }
        }
        return timedOut.size();
    }

    private String safeMessage(String message) {
        String safe = message == null || message.isBlank() ? "Code evidence collection failed" : message;
        safe = safe.replace('\r', ' ').replace('\n', ' ');
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }

    public record ClaimedEvidenceJob(Long jobId, Long runId) {}
}
