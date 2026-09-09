package com.example.agentcollab.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "member_profile_versions", uniqueConstraints = @UniqueConstraint(columnNames = {"project_member_id", "version_no"}))
public class MemberProfileVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_member_id", nullable = false)
    private Long projectMemberId;
    @Column(name = "version_no", nullable = false)
    private int versionNo;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode profile;
    @Column(name = "changed_by", nullable = false)
    private Long changedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MemberProfileVersion() {}

    public MemberProfileVersion(Long projectMemberId, int versionNo, JsonNode profile, Long changedBy) {
        this.projectMemberId = projectMemberId;
        this.versionNo = versionNo;
        this.profile = profile;
        this.changedBy = changedBy;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getProjectMemberId() { return projectMemberId; }
    public int getVersionNo() { return versionNo; }
    public JsonNode getProfile() { return profile; }
    public Long getChangedBy() { return changedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
