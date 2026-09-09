package com.example.agentcollab.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "workflows")
public class Workflow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "intent_level", nullable = false, length = 20)
    private IntentLevel intentLevel;
    @Column(name = "parent_workflow_id")
    private Long parentWorkflowId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WorkflowStatus status = WorkflowStatus.INTENT;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkflowHealth health = WorkflowHealth.HEALTHY;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    @Version
    private Long version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Workflow() {}

    public Workflow(Long projectId, String title, String description, IntentLevel intentLevel,
                    Long parentWorkflowId, Long createdBy) {
        this.projectId = projectId;
        this.title = title;
        this.description = description;
        this.intentLevel = intentLevel;
        this.parentWorkflowId = parentWorkflowId;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    void transitionTo(WorkflowStatus target) {
        this.status = target;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public IntentLevel getIntentLevel() { return intentLevel; }
    public Long getParentWorkflowId() { return parentWorkflowId; }
    public WorkflowStatus getStatus() { return status; }
    public WorkflowHealth getHealth() { return health; }
    public Long getCreatedBy() { return createdBy; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
