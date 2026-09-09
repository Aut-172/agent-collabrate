package com.example.agentcollab.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "outbox_jobs", uniqueConstraints = @UniqueConstraint(columnNames = {"job_type", "reference_id"}))
public class OutboxJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 40, updatable = false)
    private OutboxJobType jobType;
    @Column(name = "reference_id", nullable = false, updatable = false)
    private Long referenceId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxJobStatus status;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "locked_at")
    private Instant lockedAt;
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OutboxJob() {}

    public OutboxJob(OutboxJobType jobType, Long referenceId) {
        Instant now = Instant.now();
        this.jobType = jobType;
        this.referenceId = referenceId;
        this.status = OutboxJobStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void claim() {
        requireStatus(OutboxJobStatus.PENDING);
        status = OutboxJobStatus.RUNNING;
        attemptCount++;
        lockedAt = Instant.now();
        updatedAt = lockedAt;
    }

    public void retryAt(Instant nextAttempt, String message) {
        requireStatus(OutboxJobStatus.RUNNING);
        status = OutboxJobStatus.PENDING;
        nextAttemptAt = nextAttempt;
        lockedAt = null;
        errorMessage = message;
        updatedAt = Instant.now();
    }

    public void succeed() {
        requireStatus(OutboxJobStatus.RUNNING);
        status = OutboxJobStatus.SUCCEEDED;
        lockedAt = null;
        errorMessage = null;
        updatedAt = Instant.now();
    }

    public void fail(String message) {
        if (status != OutboxJobStatus.PENDING && status != OutboxJobStatus.RUNNING) {
            throw new IllegalStateException("OutboxJob is not active");
        }
        status = OutboxJobStatus.FAILED;
        lockedAt = null;
        errorMessage = message;
        updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == OutboxJobStatus.PENDING || status == OutboxJobStatus.RUNNING;
    }

    private void requireStatus(OutboxJobStatus expected) {
        if (status != expected) throw new IllegalStateException("Expected OutboxJob status " + expected);
    }

    public Long getId() { return id; }
    public OutboxJobType getJobType() { return jobType; }
    public Long getReferenceId() { return referenceId; }
    public OutboxJobStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getLockedAt() { return lockedAt; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
