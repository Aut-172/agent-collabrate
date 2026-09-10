package com.example.agentcollab.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "projects")
public class Project {
    public enum Status { ACTIVE, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "repository_url", nullable = false, length = 500)
    private String repositoryUrl;
    @Column(name = "git_provider", nullable = false, length = 30)
    private String gitProvider;
    @Column(name = "default_branch", nullable = false, length = 100)
    private String defaultBranch;
    @Column(name = "latest_context_commit", length = 100)
    private String latestContextCommit;
    @Enumerated(EnumType.STRING)
    @Column(name = "ci_status", nullable = false, length = 30)
    private ProjectCiStatus ciStatus = ProjectCiStatus.CI_NOT_CONFIGURED;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    protected Project() {}

    public Project(String name, String repositoryUrl, String gitProvider, String defaultBranch, Long createdBy) {
        this.name = name;
        this.repositoryUrl = repositoryUrl;
        this.gitProvider = gitProvider;
        this.defaultBranch = defaultBranch;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public String getGitProvider() { return gitProvider; }
    public String getDefaultBranch() { return defaultBranch; }
    public String getLatestContextCommit() { return latestContextCommit; }
    public ProjectCiStatus getCiStatus() { return ciStatus; }
    public Status getStatus() { return status; }
    public Long getCreatedBy() { return createdBy; }
    public Long getVersion() { return version; }
    public void enableCi() {
        if (ciStatus != ProjectCiStatus.CI_NOT_CONFIGURED) {
            throw new IllegalStateException("CI is already configured");
        }
        ciStatus = ProjectCiStatus.CI_REQUIRED;
        updatedAt = Instant.now();
    }
    public void archive() { status = Status.ARCHIVED; }
    public void recordContextCommit(String commitSha) {
        this.latestContextCommit = commitSha;
        this.updatedAt = Instant.now();
    }
}
