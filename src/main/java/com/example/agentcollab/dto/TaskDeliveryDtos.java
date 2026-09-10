package com.example.agentcollab.dto;

import com.example.agentcollab.domain.TaskDelivery;
import com.example.agentcollab.domain.TaskDeliveryOutcome;
import com.example.agentcollab.domain.TaskDeliveryStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

public final class TaskDeliveryDtos {
    private TaskDeliveryDtos() {}

    public record SubmitRequest(
            @NotNull Long packageId,
            @NotNull Integer packageVersion,
            @NotBlank String packageHash,
            @NotNull JsonNode finalReport,
            @NotBlank String branchName,
            @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{7,64}$") String commitSha,
            String pullRequestUrl) {}

    public record DeliveryResponse(
            Long id, Long taskId, Long submittedBy, Long packageId, int packageVersion,
            Long codeContextVersionId, Long contextPlanId, String baseCommitSha,
            TaskDeliveryOutcome outcome, JsonNode finalReport, String branchName, String commitSha,
            String pullRequestUrl, TaskDeliveryStatus status, String rejectionReason,
            Instant submittedAt, Instant reviewedAt) {
        public static DeliveryResponse from(TaskDelivery delivery) {
            return new DeliveryResponse(delivery.getId(), delivery.getTaskId(), delivery.getSubmittedBy(),
                    delivery.getPackageId(), delivery.getPackageVersion(), delivery.getCodeContextVersionId(),
                    delivery.getContextPlanId(), delivery.getBaseCommitSha(), delivery.getOutcome(),
                    delivery.getReportJson(), delivery.getBranchName(), delivery.getCommitSha(),
                    delivery.getPullRequestUrl(), delivery.getStatus(), delivery.getRejectionReason(),
                    delivery.getSubmittedAt(), delivery.getReviewedAt());
        }
    }
}
