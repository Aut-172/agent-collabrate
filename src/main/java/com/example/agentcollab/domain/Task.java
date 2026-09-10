package com.example.agentcollab.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tasks", uniqueConstraints = @UniqueConstraint(columnNames = {"workflow_id", "external_key"}))
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "workflow_id", nullable = false, updatable = false)
    private Long workflowId;
    @Column(name = "external_key", nullable = false, length = 50, updatable = false)
    private String externalKey;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    @Column(name = "effort_points", nullable = false)
    private int effortPoints;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TaskStatus status;
    @Column(name = "source_plan_version", nullable = false, updatable = false)
    private int sourcePlanVersion;
    @Column(name = "source_spec_version", updatable = false)
    private Integer sourceSpecVersion;
    @Column(name = "branch_name", length = 200)
    private String branchName;
    @Column(name = "current_package_version")
    private Integer currentPackageVersion;
    @Column(name = "delivery_notes", columnDefinition = "text")
    private String deliveryNotes;
    @Column(name = "result_summary", columnDefinition = "text")
    private String resultSummary;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    protected Task() {}

    public Task(Long workflowId, String externalKey, String title, String description,
                int effortPoints, int sourcePlanVersion, Integer sourceSpecVersion, String branchName) {
        this.workflowId = workflowId;
        this.externalKey = externalKey;
        this.title = title;
        this.description = description;
        this.effortPoints = effortPoints;
        this.sourcePlanVersion = sourcePlanVersion;
        this.sourceSpecVersion = sourceSpecVersion;
        this.branchName = branchName;
        this.status = TaskStatus.TODO;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markAssigned() {
        if (isTerminal()) throw new IllegalStateException("Task cannot be assigned from " + status);
        status = TaskStatus.ASSIGNED;
        updatedAt = Instant.now();
    }

    public void startDevelopment() {
        if (status != TaskStatus.ASSIGNED && status != TaskStatus.BLOCKED) {
            throw new IllegalStateException("Task cannot start development from " + status);
        }
        status = TaskStatus.IN_PROGRESS;
        updatedAt = Instant.now();
    }

    public void block() {
        if (status != TaskStatus.IN_PROGRESS) {
            throw new IllegalStateException("Task cannot be blocked from " + status);
        }
        status = TaskStatus.BLOCKED;
        updatedAt = Instant.now();
    }

    public void submitDelivery() {
        if (status != TaskStatus.IN_PROGRESS) {
            throw new IllegalStateException("Task cannot submit delivery from " + status);
        }
        status = TaskStatus.DELIVERY_SUBMITTED;
        updatedAt = Instant.now();
    }

    public void markCiRunning() {
        if (status != TaskStatus.DELIVERY_SUBMITTED) throw new IllegalStateException("Task cannot start CI from " + status);
        status = TaskStatus.CI_RUNNING;
        updatedAt = Instant.now();
    }

    public void completeFromCi() {
        if (status != TaskStatus.CI_RUNNING) throw new IllegalStateException("Task cannot complete from " + status);
        status = TaskStatus.DONE;
        updatedAt = Instant.now();
    }

    public void returnForCiRework() {
        if (status != TaskStatus.CI_RUNNING) throw new IllegalStateException("Task cannot return from " + status);
        status = TaskStatus.IN_PROGRESS;
        updatedAt = Instant.now();
    }

    public void returnForDeliveryRework() {
        if (status != TaskStatus.DELIVERY_SUBMITTED) {
            throw new IllegalStateException("Task cannot return from " + status);
        }
        status = TaskStatus.IN_PROGRESS;
        updatedAt = Instant.now();
    }

    public boolean isTerminal() {
        return status == TaskStatus.DONE || status == TaskStatus.FAILED || status == TaskStatus.CANCELLED;
    }

    public void setCurrentPackageVersion(int packageVersion) {
        this.currentPackageVersion = packageVersion;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (isTerminal()) return;
        status = TaskStatus.CANCELLED;
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getWorkflowId() { return workflowId; }
    public String getExternalKey() { return externalKey; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getEffortPoints() { return effortPoints; }
    public TaskStatus getStatus() { return status; }
    public int getSourcePlanVersion() { return sourcePlanVersion; }
    public Integer getSourceSpecVersion() { return sourceSpecVersion; }
    public String getBranchName() { return branchName; }
    public Integer getCurrentPackageVersion() { return currentPackageVersion; }
    public String getDeliveryNotes() { return deliveryNotes; }
    public String getResultSummary() { return resultSummary; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
