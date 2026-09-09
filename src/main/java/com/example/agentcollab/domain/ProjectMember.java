package com.example.agentcollab.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "project_members", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id"}))
public class ProjectMember {
    public enum Role { LEADER, MEMBER }
    public enum Status { ACTIVE, REMOVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "project_role", nullable = false, length = 20)
    private Role projectRole;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capability_profile", columnDefinition = "jsonb")
    private JsonNode capabilityProfile;
    @Column(name = "profile_version", nullable = false)
    private int profileVersion;
    @Column(name = "profile_completed", nullable = false)
    private boolean profileCompleted;
    @Column(name = "profile_updated_at")
    private Instant profileUpdatedAt;
    @Column(name = "weekly_capacity_points")
    private Integer weeklyCapacityPoints;
    @Column(length = 30)
    private String availability;
    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;
    @Column(name = "left_at")
    private Instant leftAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;
    @Version
    private Long version;

    protected ProjectMember() {}

    public ProjectMember(Long projectId, Long userId, Role projectRole) {
        this.projectId = projectId;
        this.userId = userId;
        this.projectRole = projectRole;
        this.joinedAt = Instant.now();
    }

    public void updateProfile(JsonNode profile, Integer weeklyCapacityPoints, String availability) {
        this.capabilityProfile = profile;
        this.weeklyCapacityPoints = weeklyCapacityPoints;
        this.availability = availability;
        this.profileVersion++;
        this.profileCompleted = true;
        this.profileUpdatedAt = Instant.now();
    }

    public void remove() {
        this.status = Status.REMOVED;
        this.leftAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getUserId() { return userId; }
    public Role getProjectRole() { return projectRole; }
    public JsonNode getCapabilityProfile() { return capabilityProfile; }
    public int getProfileVersion() { return profileVersion; }
    public boolean isProfileCompleted() { return profileCompleted; }
    public Instant getProfileUpdatedAt() { return profileUpdatedAt; }
    public Integer getWeeklyCapacityPoints() { return weeklyCapacityPoints; }
    public String getAvailability() { return availability; }
    public Instant getJoinedAt() { return joinedAt; }
    public Status getStatus() { return status; }
    public Long getVersion() { return version; }
}
