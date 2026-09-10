package com.example.agentcollab.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "git_operations")
public class GitOperation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false, updatable = false) private Long projectId;
    @Column(name = "workflow_id", nullable = false, updatable = false) private Long workflowId;
    @Column(name = "task_id", nullable = false, updatable = false) private Long taskId;
    @Column(name = "delivery_id", nullable = false, updatable = false) private Long deliveryId;
    @Enumerated(EnumType.STRING) @Column(name = "operation_type", nullable = false, length = 40, updatable = false) private GitOperationType operationType;
    @Column(name = "branch_name", length = 200, updatable = false) private String branchName;
    @Column(name = "commit_sha", length = 100, updatable = false) private String commitSha;
    @Column(name = "pull_request_number", updatable = false) private Integer pullRequestNumber;
    @Column(name = "pull_request_url", length = 500, updatable = false) private String pullRequestUrl;
    @Column(name = "external_id", length = 200, updatable = false) private String externalId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private GitOperationStatus status;
    @Column(name = "error_message", columnDefinition = "text") private String errorMessage;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected GitOperation() {}

    public GitOperation(Long projectId, Long workflowId, Long taskId, Long deliveryId,
                        String branchName, String commitSha, String pullRequestUrl) {
        this.projectId = projectId; this.workflowId = workflowId; this.taskId = taskId;
        this.deliveryId = deliveryId; this.operationType = GitOperationType.VALIDATE_DELIVERY;
        this.branchName = branchName; this.commitSha = commitSha.toLowerCase();
        this.pullRequestUrl = pullRequestUrl; this.status = GitOperationStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void succeed(String externalId) { status = GitOperationStatus.SUCCEEDED; this.externalId = externalId; errorMessage = null; }
    public void fail(String message) { status = GitOperationStatus.FAILED; errorMessage = message; }
    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getWorkflowId() { return workflowId; }
    public Long getTaskId() { return taskId; }
    public Long getDeliveryId() { return deliveryId; }
    public GitOperationType getOperationType() { return operationType; }
    public String getBranchName() { return branchName; }
    public String getCommitSha() { return commitSha; }
    public String getPullRequestUrl() { return pullRequestUrl; }
    public String getExternalId() { return externalId; }
    public GitOperationStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
