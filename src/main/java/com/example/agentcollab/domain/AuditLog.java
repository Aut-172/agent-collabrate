package com.example.agentcollab.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "actor_user_id", updatable = false) private Long actorUserId;
    @Column(name = "project_id", updatable = false) private Long projectId;
    @Column(nullable = false, length = 100, updatable = false) private String action;
    @Column(name = "entity_type", nullable = false, length = 50, updatable = false) private String entityType;
    @Column(name = "entity_id", nullable = false, updatable = false) private Long entityId;
    @Column(name = "request_id", length = 100, updatable = false) private String requestId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "details_json", columnDefinition = "jsonb", updatable = false)
    private JsonNode detailsJson;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected AuditLog() {}
    public AuditLog(Long actorUserId, Long projectId, String action, String entityType, Long entityId,
                    String requestId, JsonNode detailsJson) {
        this.actorUserId = actorUserId; this.projectId = projectId; this.action = action;
        this.entityType = entityType; this.entityId = entityId; this.requestId = requestId;
        this.detailsJson = detailsJson; this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public Long getActorUserId() { return actorUserId; }
    public Long getProjectId() { return projectId; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getRequestId() { return requestId; }
    public JsonNode getDetailsJson() { return detailsJson; }
    public Instant getCreatedAt() { return createdAt; }
}
