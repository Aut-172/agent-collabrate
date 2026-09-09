package com.example.agentcollab.dto;

import com.example.agentcollab.domain.Task;
import com.example.agentcollab.domain.TaskAssignment;
import com.example.agentcollab.domain.TaskStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public final class TaskDtos {
    private TaskDtos() {}

    public record ReassignRequest(
            @NotNull Long assigneeUserId,
            @NotBlank String reason,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal assignmentScore) {}

    public record AssignmentResponse(
            Long id,
            Long assigneeUserId,
            Long assignedBy,
            int assignmentVersion,
            String assignmentReason,
            BigDecimal assignmentScore,
            int profileVersion,
            JsonNode profileSnapshot,
            JsonNode workloadSnapshot,
            boolean current,
            Instant assignedAt,
            Instant endedAt) {
        public static AssignmentResponse from(TaskAssignment assignment) {
            if (assignment == null) return null;
            return new AssignmentResponse(assignment.getId(), assignment.getAssigneeUserId(),
                    assignment.getAssignedBy(), assignment.getAssignmentVersion(),
                    assignment.getAssignmentReason(), assignment.getAssignmentScore(),
                    assignment.getProfileVersion(), assignment.getProfileSnapshot(),
                    assignment.getWorkloadSnapshot(), assignment.isCurrent(),
                    assignment.getAssignedAt(), assignment.getEndedAt());
        }
    }

    public record TaskResponse(
            Long id,
            Long workflowId,
            String externalKey,
            String title,
            String description,
            int effortPoints,
            TaskStatus status,
            int sourcePlanVersion,
            Integer sourceSpecVersion,
            String branchName,
            Integer currentPackageVersion,
            Long version,
            JsonNode planDetails,
            AssignmentResponse currentAssignment) {
        public static TaskResponse from(Task task, JsonNode planDetails, TaskAssignment assignment) {
            return new TaskResponse(task.getId(), task.getWorkflowId(), task.getExternalKey(), task.getTitle(),
                    task.getDescription(), task.getEffortPoints(), task.getStatus(), task.getSourcePlanVersion(),
                    task.getSourceSpecVersion(), task.getBranchName(), task.getCurrentPackageVersion(),
                    task.getVersion(), planDetails, AssignmentResponse.from(assignment));
        }
    }
}
