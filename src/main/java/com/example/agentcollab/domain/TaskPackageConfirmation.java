package com.example.agentcollab.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "task_package_confirmations", uniqueConstraints = @UniqueConstraint(columnNames = {
        "task_id", "user_id", "package_version"}))
public class TaskPackageConfirmation {
    public enum Type { START_DEVELOPMENT, RESUME_AFTER_BLOCKER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "task_id", nullable = false, updatable = false)
    private Long taskId;
    @Column(name = "package_id", nullable = false, updatable = false)
    private Long packageId;
    @Column(name = "package_version", nullable = false, updatable = false)
    private int packageVersion;
    @Column(name = "content_hash", nullable = false, length = 128, updatable = false)
    private String contentHash;
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_type", nullable = false, length = 30, updatable = false)
    private Type confirmationType;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskPackageConfirmation() {}

    public TaskPackageConfirmation(Long taskId, Long packageId, int packageVersion, String contentHash,
                                   Long userId, Type confirmationType) {
        this.taskId = taskId;
        this.packageId = packageId;
        this.packageVersion = packageVersion;
        this.contentHash = contentHash;
        this.userId = userId;
        this.confirmationType = confirmationType;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public Long getPackageId() { return packageId; }
    public int getPackageVersion() { return packageVersion; }
    public String getContentHash() { return contentHash; }
    public Long getUserId() { return userId; }
    public Type getConfirmationType() { return confirmationType; }
    public Instant getCreatedAt() { return createdAt; }
}
