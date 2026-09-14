package com.example.agentcollab.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "agent_call_records")
public class AgentCallRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "agent_run_id", nullable = false, updatable = false)
    private Long agentRunId;
    @Column(name = "attempt_no", nullable = false, updatable = false)
    private int attemptNo;
    @Column(nullable = false, length = 50, updatable = false)
    private String provider;
    @Column(nullable = false, length = 100, updatable = false)
    private String model;
    @Column(name = "run_type", nullable = false, length = 40, updatable = false)
    private String runType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private AgentCallStatus status;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "request_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private JsonNode requestJson;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "response_json", columnDefinition = "jsonb")
    private JsonNode responseJson;
    @Column(name = "error_code", length = 100) private String errorCode;
    @Column(name = "error_message", columnDefinition = "text") private String errorMessage;
    @Column(name = "retryable") private Boolean retryable;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "started_at", nullable = false, updatable = false) private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "duration_ms") private Long durationMs;

    protected AgentCallRecord() {}

    public AgentCallRecord(Long agentRunId, int attemptNo, String provider, String model, String runType,
                           JsonNode requestJson) {
        this.agentRunId = agentRunId;
        this.attemptNo = attemptNo;
        this.provider = provider;
        this.model = model;
        this.runType = runType;
        this.status = AgentCallStatus.RUNNING;
        this.requestJson = requestJson;
        this.createdAt = Instant.now();
        this.startedAt = this.createdAt;
    }

    public void succeed(JsonNode responseJson, long durationMs) {
        requireRunning();
        this.status = AgentCallStatus.SUCCEEDED;
        this.responseJson = responseJson;
        this.finishedAt = Instant.now();
        this.durationMs = Math.max(0, durationMs);
        this.errorCode = null;
        this.errorMessage = null;
        this.retryable = null;
    }

    public void fail(String errorCode, String errorMessage, boolean retryable, long durationMs) {
        fail(null, errorCode, errorMessage, retryable, durationMs);
    }

    public void fail(JsonNode responseJson, String errorCode, String errorMessage,
                     boolean retryable, long durationMs) {
        requireRunning();
        this.status = AgentCallStatus.FAILED;
        this.responseJson = responseJson;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.retryable = retryable;
        this.finishedAt = Instant.now();
        this.durationMs = Math.max(0, durationMs);
    }

    private void requireRunning() {
        if (status != AgentCallStatus.RUNNING) {
            throw new IllegalStateException("AgentCallRecord is not running");
        }
    }

    public Long getId() { return id; }
    public Long getAgentRunId() { return agentRunId; }
    public int getAttemptNo() { return attemptNo; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getRunType() { return runType; }
    public AgentCallStatus getStatus() { return status; }
    public JsonNode getRequestJson() { return requestJson; }
    public JsonNode getResponseJson() { return responseJson; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Boolean getRetryable() { return retryable; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Long getDurationMs() { return durationMs; }
}
