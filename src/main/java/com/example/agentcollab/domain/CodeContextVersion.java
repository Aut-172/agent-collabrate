package com.example.agentcollab.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "code_context_versions")
public class CodeContextVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false, updatable = false) private Long projectId;
    @Column(nullable = false, length = 30, updatable = false) private String provider;
    @Column(name = "inventory_version_id", updatable = false) private Long inventoryVersionId;
    @Column(name = "context_plan_id", updatable = false) private Long contextPlanId;
    @Column(name = "repository_url", nullable = false, length = 500, updatable = false) private String repositoryUrl;
    @Column(name = "branch_name", nullable = false, length = 100, updatable = false) private String branchName;
    @Column(name = "base_commit_sha", nullable = false, length = 100, updatable = false) private String baseCommitSha;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CodeContextStatus status;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "repository_profile", nullable = false, columnDefinition = "jsonb", updatable = false)
    private JsonNode repositoryProfile;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "evidence_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private JsonNode evidenceJson;
    @Column(name = "error_message", columnDefinition = "text") private String errorMessage;
    @Column(name = "created_by", updatable = false) private Long createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected CodeContextVersion() {}
    public CodeContextVersion(Long projectId, Long inventoryVersionId, Long contextPlanId, String repositoryUrl,
                              String branchName, String baseCommitSha, JsonNode repositoryProfile,
                              JsonNode evidenceJson, Long createdBy) {
        this.projectId = projectId; this.provider = "GIT"; this.inventoryVersionId = inventoryVersionId;
        this.contextPlanId = contextPlanId; this.repositoryUrl = repositoryUrl; this.branchName = branchName;
        this.baseCommitSha = baseCommitSha; this.repositoryProfile = repositoryProfile.deepCopy();
        this.evidenceJson = evidenceJson.deepCopy(); this.createdBy = createdBy;
        this.status = CodeContextStatus.CURRENT; this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }
    public void markStale() { if (status == CodeContextStatus.CURRENT) { status = CodeContextStatus.STALE; updatedAt = Instant.now(); } }
    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getProvider() { return provider; }
    public Long getInventoryVersionId() { return inventoryVersionId; }
    public Long getContextPlanId() { return contextPlanId; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public String getBranchName() { return branchName; }
    public String getBaseCommitSha() { return baseCommitSha; }
    public CodeContextStatus getStatus() { return status; }
    public JsonNode getRepositoryProfile() { return repositoryProfile.deepCopy(); }
    public JsonNode getEvidenceJson() { return evidenceJson.deepCopy(); }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
