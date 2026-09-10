package com.example.agentcollab.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "task_packages", uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "package_version"}))
public class TaskPackage {
    @Id
    private Long id;
    @Column(name = "task_id", nullable = false, updatable = false) private Long taskId;
    @Column(name = "package_version", nullable = false, updatable = false) private int packageVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private TaskPackageStatus status;
    @Column(name = "content_markdown", nullable = false, columnDefinition = "text", updatable = false) private String contentMarkdown;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "content_json", nullable = false, columnDefinition = "jsonb", updatable = false) private JsonNode contentJson;
    @Column(name = "content_hash", nullable = false, length = 128, updatable = false) private String contentHash;
    @Column(name = "source_task_version", nullable = false, updatable = false) private Long sourceTaskVersion;
    @Column(name = "source_plan_version", nullable = false, updatable = false) private int sourcePlanVersion;
    @Column(name = "source_spec_version", updatable = false) private Integer sourceSpecVersion;
    @Column(name = "source_profile_version", updatable = false) private Integer sourceProfileVersion;
    @Column(name = "base_commit", nullable = false, length = 100, updatable = false) private String baseCommit;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "superseded_by") private Long supersededBy;

    protected TaskPackage() {}
    public TaskPackage(Long id, Long taskId, int packageVersion, String markdown, JsonNode contentJson, String hash,
                       Long taskVersion, int planVersion, Integer specVersion, Integer profileVersion, String baseCommit) {
        this.id = id; this.taskId = taskId; this.packageVersion = packageVersion; this.status = TaskPackageStatus.CURRENT;
        this.contentMarkdown = markdown; this.contentJson = contentJson.deepCopy(); this.contentHash = hash;
        this.sourceTaskVersion = taskVersion; this.sourcePlanVersion = planVersion; this.sourceSpecVersion = specVersion;
        this.sourceProfileVersion = profileVersion; this.baseCommit = baseCommit; this.createdAt = Instant.now();
    }
    public void markStale() {
        if (status != TaskPackageStatus.CURRENT) throw new IllegalStateException("TaskPackage is not current");
        status = TaskPackageStatus.STALE;
    }
    public void supersedeWith(Long packageId) {
        if (status != TaskPackageStatus.STALE) throw new IllegalStateException("TaskPackage is not stale");
        this.supersededBy = packageId;
    }
    public void retire() {
        if (status == TaskPackageStatus.CURRENT) status = TaskPackageStatus.RETIRED;
    }
    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public int getPackageVersion() { return packageVersion; }
    public TaskPackageStatus getStatus() { return status; }
    public String getContentMarkdown() { return contentMarkdown; }
    public JsonNode getContentJson() { return contentJson.deepCopy(); }
    public String getContentHash() { return contentHash; }
    public Long getSourceTaskVersion() { return sourceTaskVersion; }
    public int getSourcePlanVersion() { return sourcePlanVersion; }
    public Integer getSourceSpecVersion() { return sourceSpecVersion; }
    public Integer getSourceProfileVersion() { return sourceProfileVersion; }
    public String getBaseCommit() { return baseCommit; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getSupersededBy() { return supersededBy; }
}
