package com.example.agentcollab.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ci_runs", uniqueConstraints = @UniqueConstraint(columnNames = {"delivery_id", "commit_sha"}))
public class CiRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false, updatable = false) private Long projectId;
    @Column(name = "workflow_id", nullable = false, updatable = false) private Long workflowId;
    @Column(name = "task_id", nullable = false, updatable = false) private Long taskId;
    @Column(name = "delivery_id", nullable = false, updatable = false) private Long deliveryId;
    @Column(name = "commit_sha", nullable = false, length = 100, updatable = false) private String commitSha;
    @Column(name = "external_id", length = 200) private String externalId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private CiRunStatus status;
    @Column(length = 50) private String conclusion;
    @Column(name = "details_url", length = 500) private String detailsUrl;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "last_synced_at", nullable = false) private Instant lastSyncedAt;
    @Column(name = "configuration_present") private Boolean configurationPresent;
    @Column(name = "configuration_recognized") private Boolean configurationRecognized;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected CiRun() {}

    public CiRun(Long projectId, Long workflowId, Long taskId, Long deliveryId, String commitSha) {
        Instant now = Instant.now();
        this.projectId = projectId; this.workflowId = workflowId; this.taskId = taskId;
        this.deliveryId = deliveryId; this.commitSha = commitSha.toLowerCase();
        this.status = CiRunStatus.PENDING;
        this.lastSyncedAt = now; this.createdAt = now;
    }

    public void pending(String conclusion, String detailsUrl) {
        status = CiRunStatus.PENDING;
        updateObservation(conclusion, detailsUrl, null, null);
    }

    public void running(String conclusion, String detailsUrl) {
        status = CiRunStatus.RUNNING;
        if (startedAt == null) startedAt = Instant.now();
        updateObservation(conclusion, detailsUrl, null, null);
    }

    public void passed(String conclusion, String detailsUrl,
                       boolean configurationPresent, boolean configurationRecognized) {
        status = CiRunStatus.PASSED;
        if (startedAt == null) startedAt = Instant.now();
        finishedAt = Instant.now();
        updateObservation(conclusion, detailsUrl, configurationPresent, configurationRecognized);
    }

    public void failed(String conclusion, String detailsUrl,
                       boolean configurationPresent, boolean configurationRecognized) {
        status = CiRunStatus.FAILED;
        if (startedAt == null) startedAt = Instant.now();
        finishedAt = Instant.now();
        updateObservation(conclusion, detailsUrl, configurationPresent, configurationRecognized);
    }

    public void unknown(String conclusion, String detailsUrl) {
        status = CiRunStatus.UNKNOWN;
        updateObservation(conclusion, detailsUrl, null, null);
    }

    private void updateObservation(String conclusion, String detailsUrl,
                                   Boolean configurationPresent, Boolean configurationRecognized) {
        this.conclusion = conclusion;
        this.detailsUrl = detailsUrl;
        this.configurationPresent = configurationPresent;
        this.configurationRecognized = configurationRecognized;
        lastSyncedAt = Instant.now();
    }

    public boolean isTerminal() {
        return status == CiRunStatus.PASSED || status == CiRunStatus.FAILED;
    }

    public void identify(String externalId) {
        if (externalId != null && !externalId.isBlank()) this.externalId = externalId;
    }
    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getWorkflowId() { return workflowId; }
    public Long getTaskId() { return taskId; }
    public Long getDeliveryId() { return deliveryId; }
    public String getCommitSha() { return commitSha; }
    public String getExternalId() { return externalId; }
    public CiRunStatus getStatus() { return status; }
    public String getConclusion() { return conclusion; }
    public String getDetailsUrl() { return detailsUrl; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public Boolean getConfigurationPresent() { return configurationPresent; }
    public Boolean getConfigurationRecognized() { return configurationRecognized; }
    public Instant getCreatedAt() { return createdAt; }
}
