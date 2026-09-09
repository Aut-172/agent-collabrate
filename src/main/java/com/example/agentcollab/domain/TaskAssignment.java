package com.example.agentcollab.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "task_assignments", uniqueConstraints = @UniqueConstraint(columnNames = {
        "task_id", "assignment_version"}))
public class TaskAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "task_id", nullable = false, updatable = false)
    private Long taskId;
    @Column(name = "assignee_user_id", nullable = false, updatable = false)
    private Long assigneeUserId;
    @Column(name = "assigned_by", nullable = false, updatable = false)
    private Long assignedBy;
    @Column(name = "assignment_version", nullable = false, updatable = false)
    private int assignmentVersion;
    @Column(name = "assignment_reason", nullable = false, columnDefinition = "text", updatable = false)
    private String assignmentReason;
    @Column(name = "assignment_score", precision = 5, scale = 4, updatable = false)
    private BigDecimal assignmentScore;
    @Column(name = "profile_version", nullable = false, updatable = false)
    private int profileVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_snapshot", nullable = false, columnDefinition = "jsonb", updatable = false)
    private JsonNode profileSnapshot;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "workload_snapshot", nullable = false, columnDefinition = "jsonb", updatable = false)
    private JsonNode workloadSnapshot;
    @Column(name = "is_current", nullable = false)
    private boolean current;
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;
    @Column(name = "ended_at")
    private Instant endedAt;

    protected TaskAssignment() {}

    public TaskAssignment(Long taskId, Long assigneeUserId, Long assignedBy, int assignmentVersion,
                          String assignmentReason, BigDecimal assignmentScore, int profileVersion,
                          JsonNode profileSnapshot, JsonNode workloadSnapshot) {
        this.taskId = taskId;
        this.assigneeUserId = assigneeUserId;
        this.assignedBy = assignedBy;
        this.assignmentVersion = assignmentVersion;
        this.assignmentReason = assignmentReason;
        this.assignmentScore = assignmentScore;
        this.profileVersion = profileVersion;
        this.profileSnapshot = profileSnapshot.deepCopy();
        this.workloadSnapshot = workloadSnapshot.deepCopy();
        this.current = true;
        this.assignedAt = Instant.now();
    }

    public void end() {
        if (!current) throw new IllegalStateException("TaskAssignment is not current");
        current = false;
        endedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public Long getAssigneeUserId() { return assigneeUserId; }
    public Long getAssignedBy() { return assignedBy; }
    public int getAssignmentVersion() { return assignmentVersion; }
    public String getAssignmentReason() { return assignmentReason; }
    public BigDecimal getAssignmentScore() { return assignmentScore; }
    public int getProfileVersion() { return profileVersion; }
    public JsonNode getProfileSnapshot() { return profileSnapshot.deepCopy(); }
    public JsonNode getWorkloadSnapshot() { return workloadSnapshot.deepCopy(); }
    public boolean isCurrent() { return current; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getEndedAt() { return endedAt; }
}
