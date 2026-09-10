package com.example.agentcollab.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "agent_runs")
public class AgentRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "workflow_id", nullable = false, updatable = false)
    private Long workflowId;
    @Column(name = "task_id", updatable = false)
    private Long taskId;
    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 40, updatable = false)
    private AgentRunType runType;
    @Column(nullable = false, length = 50, updatable = false)
    private String provider;
    @Column(nullable = false, length = 100, updatable = false)
    private String model;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentRunStatus status;
    @Column(name = "request_summary", nullable = false, columnDefinition = "text", updatable = false)
    private String requestSummary;
    @Column(name = "code_context_version_id") private Long codeContextVersionId;
    @Column(name = "context_plan_id") private Long contextPlanId;
    @Column(name = "inventory_version_id") private Long inventoryVersionId;
    @Column(name = "response_summary", columnDefinition = "text")
    private String responseSummary;
    @Column(name = "retry_count", nullable = false)
    private int retryCount;
    @Column(name = "error_code", length = 100)
    private String errorCode;
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;

    protected AgentRun() {}

    public AgentRun(Long workflowId, AgentRunType runType, String provider, String model, String requestSummary) {
        this.workflowId = workflowId;
        this.runType = runType;
        this.provider = provider;
        this.model = model;
        this.requestSummary = requestSummary;
        this.status = AgentRunStatus.QUEUED;
        this.createdAt = Instant.now();
    }

    public void start() {
        requireStatus(AgentRunStatus.QUEUED);
        status = AgentRunStatus.RUNNING;
        startedAt = Instant.now();
    }

    public void recordRetry(String code, String message) {
        requireStatus(AgentRunStatus.RUNNING);
        retryCount++;
        errorCode = code;
        errorMessage = message;
    }

    public void succeed(String summary) {
        requireStatus(AgentRunStatus.RUNNING);
        status = AgentRunStatus.SUCCEEDED;
        responseSummary = summary;
        errorCode = null;
        errorMessage = null;
        finishedAt = Instant.now();
    }

    public void bindContextPlan(Long contextPlanId) { this.contextPlanId = contextPlanId; }
    public void bindInventoryVersion(Long inventoryVersionId) { this.inventoryVersionId = inventoryVersionId; }
    public void bindCodeContext(Long contextPlanId, Long codeContextVersionId) {
        this.contextPlanId = contextPlanId; this.codeContextVersionId = codeContextVersionId;
    }

    public void fail(String code, String message) {
        if (status != AgentRunStatus.QUEUED && status != AgentRunStatus.RUNNING) {
            throw new IllegalStateException("AgentRun is not active");
        }
        status = AgentRunStatus.FAILED;
        errorCode = code;
        errorMessage = message;
        finishedAt = Instant.now();
    }

    public void cancel() {
        if (!isActive()) throw new IllegalStateException("AgentRun is not active");
        status = AgentRunStatus.CANCELLED;
        finishedAt = Instant.now();
    }

    public boolean isActive() {
        return status == AgentRunStatus.QUEUED || status == AgentRunStatus.RUNNING;
    }

    private void requireStatus(AgentRunStatus expected) {
        if (status != expected) throw new IllegalStateException("Expected AgentRun status " + expected);
    }

    public Long getId() { return id; }
    public Long getWorkflowId() { return workflowId; }
    public Long getTaskId() { return taskId; }
    public AgentRunType getRunType() { return runType; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public AgentRunStatus getStatus() { return status; }
    public String getRequestSummary() { return requestSummary; }
    public Long getCodeContextVersionId() { return codeContextVersionId; }
    public Long getContextPlanId() { return contextPlanId; }
    public Long getInventoryVersionId() { return inventoryVersionId; }
    public String getResponseSummary() { return responseSummary; }
    public int getRetryCount() { return retryCount; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
