package com.example.agentcollab.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "code_context_runs")
public class CodeContextRun {
    public enum Type { REPO_INGESTION }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false, updatable = false) private Long projectId;
    @Column(name = "requested_by", updatable = false) private Long requestedBy;
    @Enumerated(EnumType.STRING) @Column(name = "run_type", nullable = false, length = 30, updatable = false)
    private Type runType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CodeContextRunStatus status;
    @Column(name = "inventory_version_id") private Long inventoryVersionId;
    @Column(name = "error_message", columnDefinition = "text") private String errorMessage;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;

    protected CodeContextRun() {}

    public CodeContextRun(Long projectId, Long requestedBy) {
        this.projectId = projectId;
        this.requestedBy = requestedBy;
        this.runType = Type.REPO_INGESTION;
        this.status = CodeContextRunStatus.QUEUED;
        this.createdAt = Instant.now();
    }

    public void start() {
        requireStatus(CodeContextRunStatus.QUEUED);
        status = CodeContextRunStatus.RUNNING;
        startedAt = Instant.now();
    }

    public void succeed(Long inventoryVersionId) {
        requireStatus(CodeContextRunStatus.RUNNING);
        this.inventoryVersionId = inventoryVersionId;
        status = CodeContextRunStatus.SUCCEEDED;
        errorMessage = null;
        finishedAt = Instant.now();
    }

    public void fail(String message) {
        if (status != CodeContextRunStatus.QUEUED && status != CodeContextRunStatus.RUNNING) {
            throw new IllegalStateException("CodeContextRun is not active");
        }
        status = CodeContextRunStatus.FAILED;
        errorMessage = message;
        finishedAt = Instant.now();
    }

    public boolean isActive() {
        return status == CodeContextRunStatus.QUEUED || status == CodeContextRunStatus.RUNNING;
    }

    private void requireStatus(CodeContextRunStatus expected) {
        if (status != expected) throw new IllegalStateException("Expected CodeContextRun status " + expected);
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getRequestedBy() { return requestedBy; }
    public Type getRunType() { return runType; }
    public CodeContextRunStatus getStatus() { return status; }
    public Long getInventoryVersionId() { return inventoryVersionId; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
