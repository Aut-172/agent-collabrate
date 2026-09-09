package com.example.agentcollab.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "workflow_members", uniqueConstraints = @UniqueConstraint(columnNames = {"workflow_id", "user_id"}))
public class WorkflowMember {
    public enum Role { OWNER, PARTICIPANT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "workflow_id", nullable = false)
    private Long workflowId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 20)
    private Role memberRole;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkflowMember() {}

    public WorkflowMember(Long workflowId, Long userId, Role memberRole) {
        this.workflowId = workflowId;
        this.userId = userId;
        this.memberRole = memberRole;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getWorkflowId() { return workflowId; }
    public Long getUserId() { return userId; }
    public Role getMemberRole() { return memberRole; }
    public Instant getCreatedAt() { return createdAt; }
}
