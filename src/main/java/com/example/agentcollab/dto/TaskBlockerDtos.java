package com.example.agentcollab.dto;

import com.example.agentcollab.domain.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class TaskBlockerDtos {
    private TaskBlockerDtos() {}

    public record ReportRequest(
            Long deliveryId,
            @NotNull TaskBlockerReason reasonCode,
            @NotBlank @Size(max = 500) String summary,
            @Size(max = 20000) String details,
            JsonNode evidence,
            @Size(max = 10000) String question) {}

    public record CloseRequest(@NotBlank @Size(max = 20000) String resolution) {}

    public record BlockerResponse(
            Long id, Long taskId, Long deliveryId, TaskBlockerStatus status,
            TaskBlockerReason reasonCode, String summary, String details, JsonNode evidence,
            String question, Long reportedBy, Long resolvedBy, String resolution,
            Instant createdAt, Instant resolvedAt, TaskStatus taskStatus,
            WorkflowHealth workflowHealth, Integer currentPackageVersion) {
        public static BlockerResponse from(TaskBlocker blocker, Task task, Workflow workflow) {
            return new BlockerResponse(blocker.getId(), blocker.getTaskId(), blocker.getDeliveryId(),
                    blocker.getStatus(), blocker.getReasonCode(), blocker.getSummary(), blocker.getDetails(),
                    blocker.getEvidenceJson(), blocker.getQuestion(), blocker.getReportedBy(),
                    blocker.getResolvedBy(), blocker.getResolution(), blocker.getCreatedAt(),
                    blocker.getResolvedAt(), task.getStatus(), workflow.getHealth(),
                    task.getCurrentPackageVersion());
        }
    }
}
