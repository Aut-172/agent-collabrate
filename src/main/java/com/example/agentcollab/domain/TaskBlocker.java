package com.example.agentcollab.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "task_blockers")
public class TaskBlocker {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "task_id", nullable = false, updatable = false) private Long taskId;
    @Column(name = "delivery_id", updatable = false) private Long deliveryId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private TaskBlockerStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "reason_code", nullable = false, length = 50, updatable = false)
    private TaskBlockerReason reasonCode;
    @Column(nullable = false, length = 500, updatable = false) private String summary;
    @Column(columnDefinition = "text", updatable = false) private String details;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "evidence_json", columnDefinition = "jsonb", updatable = false)
    private JsonNode evidenceJson;
    @Column(columnDefinition = "text", updatable = false) private String question;
    @Column(name = "reported_by", nullable = false, updatable = false) private Long reportedBy;
    @Column(name = "resolved_by") private Long resolvedBy;
    @Column(columnDefinition = "text") private String resolution;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "resolved_at") private Instant resolvedAt;

    protected TaskBlocker() {}

    public TaskBlocker(Long taskId, Long deliveryId, TaskBlockerReason reasonCode, String summary,
                       String details, JsonNode evidenceJson, String question, Long reportedBy) {
        this.taskId = taskId; this.deliveryId = deliveryId; this.reasonCode = reasonCode;
        this.summary = summary; this.details = details;
        this.evidenceJson = evidenceJson == null ? null : evidenceJson.deepCopy();
        this.question = question; this.reportedBy = reportedBy;
        this.status = TaskBlockerStatus.OPEN; this.createdAt = Instant.now();
    }

    public void resolve(Long actorId, String resolution) { close(TaskBlockerStatus.RESOLVED, actorId, resolution); }
    public void cancel(Long actorId, String resolution) { close(TaskBlockerStatus.CANCELLED, actorId, resolution); }

    private void close(TaskBlockerStatus target, Long actorId, String resolution) {
        if (status != TaskBlockerStatus.OPEN) throw new IllegalStateException("TaskBlocker is not open");
        this.status = target; this.resolvedBy = actorId; this.resolution = resolution;
        this.resolvedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public Long getDeliveryId() { return deliveryId; }
    public TaskBlockerStatus getStatus() { return status; }
    public TaskBlockerReason getReasonCode() { return reasonCode; }
    public String getSummary() { return summary; }
    public String getDetails() { return details; }
    public JsonNode getEvidenceJson() { return evidenceJson == null ? null : evidenceJson.deepCopy(); }
    public String getQuestion() { return question; }
    public Long getReportedBy() { return reportedBy; }
    public Long getResolvedBy() { return resolvedBy; }
    public String getResolution() { return resolution; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
