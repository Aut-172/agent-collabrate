package com.example.agentcollab.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "task_deliveries", uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "commit_sha"}))
public class TaskDelivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "task_id", nullable = false, updatable = false) private Long taskId;
    @Column(name = "submitted_by", nullable = false, updatable = false) private Long submittedBy;
    @Column(name = "package_id", nullable = false, updatable = false) private Long packageId;
    @Column(name = "package_version", nullable = false, updatable = false) private int packageVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30, updatable = false) private TaskDeliveryOutcome outcome;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "report_json", nullable = false, columnDefinition = "jsonb", updatable = false) private JsonNode reportJson;
    @Column(name = "branch_name", nullable = false, length = 200, updatable = false) private String branchName;
    @Column(name = "commit_sha", nullable = false, length = 100, updatable = false) private String commitSha;
    @Column(name = "pull_request_url", length = 500, updatable = false) private String pullRequestUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private TaskDeliveryStatus status;
    @Column(name = "rejection_reason", columnDefinition = "text") private String rejectionReason;
    @Column(name = "submitted_at", nullable = false, updatable = false) private Instant submittedAt;
    @Column(name = "reviewed_at", updatable = false) private Instant reviewedAt;

    protected TaskDelivery() {}

    public TaskDelivery(Long taskId, Long submittedBy, Long packageId, int packageVersion,
                        JsonNode reportJson, String branchName, String commitSha, String pullRequestUrl) {
        this.taskId = taskId; this.submittedBy = submittedBy; this.packageId = packageId;
        this.packageVersion = packageVersion; this.outcome = TaskDeliveryOutcome.READY_FOR_REVIEW;
        this.reportJson = reportJson.deepCopy(); this.branchName = branchName;
        this.commitSha = commitSha.toLowerCase(); this.pullRequestUrl = pullRequestUrl;
        this.status = TaskDeliveryStatus.SUBMITTED; this.submittedAt = Instant.now();
        this.reviewedAt = this.submittedAt;
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public Long getSubmittedBy() { return submittedBy; }
    public Long getPackageId() { return packageId; }
    public int getPackageVersion() { return packageVersion; }
    public TaskDeliveryOutcome getOutcome() { return outcome; }
    public JsonNode getReportJson() { return reportJson.deepCopy(); }
    public String getBranchName() { return branchName; }
    public String getCommitSha() { return commitSha; }
    public String getPullRequestUrl() { return pullRequestUrl; }
    public TaskDeliveryStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public void markCiRunning() {
        requireStatus(TaskDeliveryStatus.SUBMITTED);
        status = TaskDeliveryStatus.CI_RUNNING;
    }
    public void markPassed() {
        requireStatus(TaskDeliveryStatus.CI_RUNNING);
        status = TaskDeliveryStatus.PASSED;
    }
    public void markFailed(String reason) {
        requireStatus(TaskDeliveryStatus.CI_RUNNING);
        status = TaskDeliveryStatus.FAILED;
        rejectionReason = reason;
    }
    public void reject(String reason) {
        requireStatus(TaskDeliveryStatus.SUBMITTED);
        status = TaskDeliveryStatus.REJECTED;
        rejectionReason = reason;
    }

    private void requireStatus(TaskDeliveryStatus expected) {
        if (status != expected) throw new IllegalStateException("Expected TaskDelivery status " + expected);
    }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
}
