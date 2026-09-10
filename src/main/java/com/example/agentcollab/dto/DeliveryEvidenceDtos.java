package com.example.agentcollab.dto;

import com.example.agentcollab.domain.*;

import java.time.Instant;

public final class DeliveryEvidenceDtos {
    private DeliveryEvidenceDtos() {}

    public record GitOperationResponse(
            Long id, Long projectId, Long workflowId, Long taskId, Long deliveryId, GitOperationType operationType,
            String branchName, String commitSha, String pullRequestUrl, String externalId,
            GitOperationStatus status, String errorMessage, Instant createdAt) {
        public static GitOperationResponse from(GitOperation operation) {
            return new GitOperationResponse(operation.getId(), operation.getProjectId(), operation.getWorkflowId(),
                    operation.getTaskId(), operation.getDeliveryId(),
                    operation.getOperationType(), operation.getBranchName(), operation.getCommitSha(),
                    operation.getPullRequestUrl(), operation.getExternalId(), operation.getStatus(),
                    operation.getErrorMessage(), operation.getCreatedAt());
        }
    }

    public record CiRunResponse(
            Long id, Long projectId, Long workflowId, Long taskId, Long deliveryId,
            String commitSha, String externalId,
            CiRunStatus status, String conclusion, String detailsUrl,
            Boolean configurationPresent, Boolean configurationRecognized,
            Instant startedAt, Instant finishedAt, Instant lastSyncedAt, Instant createdAt) {
        public static CiRunResponse from(CiRun run) {
            return new CiRunResponse(run.getId(), run.getProjectId(), run.getWorkflowId(),
                    run.getTaskId(), run.getDeliveryId(), run.getCommitSha(),
                    run.getExternalId(), run.getStatus(), run.getConclusion(), run.getDetailsUrl(),
                    run.getConfigurationPresent(), run.getConfigurationRecognized(), run.getStartedAt(),
                    run.getFinishedAt(), run.getLastSyncedAt(), run.getCreatedAt());
        }
    }
}
