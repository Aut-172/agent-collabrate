package com.example.agentcollab.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "repo_inventory_versions", uniqueConstraints = @UniqueConstraint(columnNames = {
        "project_id", "provider", "branch_name", "commit_sha"}))
public class RepoInventoryVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false, updatable = false) private Long projectId;
    @Column(nullable = false, length = 30, updatable = false) private String provider;
    @Column(name = "repository_url", nullable = false, length = 500, updatable = false) private String repositoryUrl;
    @Column(name = "branch_name", nullable = false, length = 100, updatable = false) private String branchName;
    @Column(name = "commit_sha", nullable = false, length = 100, updatable = false) private String commitSha;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RepoInventoryStatus status;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "repository_profile", nullable = false, columnDefinition = "jsonb", updatable = false)
    private JsonNode repositoryProfile;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "tree_summary", nullable = false, columnDefinition = "jsonb", updatable = false)
    private JsonNode treeSummary;
    @Column(name = "error_message", columnDefinition = "text") private String errorMessage;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected RepoInventoryVersion() {}

    public RepoInventoryVersion(Long projectId, String repositoryUrl, String branchName, String commitSha,
                                JsonNode repositoryProfile, JsonNode treeSummary) {
        this.projectId = projectId;
        this.provider = "GIT";
        this.repositoryUrl = repositoryUrl;
        this.branchName = branchName;
        this.commitSha = commitSha;
        this.status = RepoInventoryStatus.CURRENT;
        this.repositoryProfile = repositoryProfile.deepCopy();
        this.treeSummary = treeSummary.deepCopy();
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void markStale() {
        if (status == RepoInventoryStatus.CURRENT) {
            status = RepoInventoryStatus.STALE;
            updatedAt = Instant.now();
        }
    }

    public void markCurrent() {
        if (status != RepoInventoryStatus.CURRENT) {
            status = RepoInventoryStatus.CURRENT;
            errorMessage = null;
            updatedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getProvider() { return provider; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public String getBranchName() { return branchName; }
    public String getCommitSha() { return commitSha; }
    public RepoInventoryStatus getStatus() { return status; }
    public JsonNode getRepositoryProfile() { return repositoryProfile.deepCopy(); }
    public JsonNode getTreeSummary() { return treeSummary.deepCopy(); }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
