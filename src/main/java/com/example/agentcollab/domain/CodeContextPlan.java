package com.example.agentcollab.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "code_context_plans")
public class CodeContextPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false, updatable = false) private Long projectId;
    @Column(name = "workflow_id", nullable = false, updatable = false) private Long workflowId;
    @Column(name = "inventory_version_id", nullable = false, updatable = false) private Long inventoryVersionId;
    @Column(name = "agent_run_id", updatable = false) private Long agentRunId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "plan_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private JsonNode planJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CodeContextPlanStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected CodeContextPlan() {}
    public CodeContextPlan(Long projectId, Long workflowId, Long inventoryVersionId, Long agentRunId, JsonNode planJson) {
        this.projectId = projectId; this.workflowId = workflowId; this.inventoryVersionId = inventoryVersionId;
        this.agentRunId = agentRunId; this.planJson = planJson.deepCopy();
        this.status = CodeContextPlanStatus.PROPOSED; this.createdAt = Instant.now();
    }
    public void markUsed() { if (status == CodeContextPlanStatus.PROPOSED) status = CodeContextPlanStatus.USED; }
    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getWorkflowId() { return workflowId; }
    public Long getInventoryVersionId() { return inventoryVersionId; }
    public Long getAgentRunId() { return agentRunId; }
    public JsonNode getPlanJson() { return planJson.deepCopy(); }
    public CodeContextPlanStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
