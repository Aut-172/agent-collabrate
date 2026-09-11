package com.example.agentcollab.dto;

import com.example.agentcollab.domain.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class WorkflowDtos {
    private WorkflowDtos() {}

    public record CreateWorkflowRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 20000) String description,
            @NotNull IntentLevel intentLevel,
            Long parentWorkflowId,
            WorkflowCompletionMode completionMode) {}

    public record CreateCiBootstrapRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 20000) String description) {}

    public record SaveDocumentRequest(@NotBlank @Size(max = 1000000) String content) {}

    public record ConfirmDocumentRequest(@Min(1) int versionNo) {}

    public record WorkflowResponse(
            Long id,
            Long projectId,
            String title,
            String description,
            IntentLevel intentLevel,
            WorkflowCompletionMode completionMode,
            Long parentWorkflowId,
            WorkflowStatus status,
            WorkflowHealth health,
            Long createdBy,
            Long version,
            Instant createdAt,
            Instant updatedAt,
            String nextAction) {
        public static WorkflowResponse from(Workflow workflow) {
            return new WorkflowResponse(workflow.getId(), workflow.getProjectId(), workflow.getTitle(),
                    workflow.getDescription(), workflow.getIntentLevel(), workflow.getCompletionMode(),
                    workflow.getParentWorkflowId(),
                    workflow.getStatus(), workflow.getHealth(), workflow.getCreatedBy(), workflow.getVersion(),
                    workflow.getCreatedAt(), workflow.getUpdatedAt(), nextAction(workflow));
        }

        private static String nextAction(Workflow workflow) {
            return switch (workflow.getStatus()) {
                case INTENT -> workflow.getIntentLevel() == IntentLevel.CHANGE
                        ? "GENERATE_BUILD_PLAN" : "GENERATE_DESIGN";
                case DESIGN_PROPOSED -> "CONFIRM_DESIGN_OR_GENERATE_SPEC";
                case SPEC_PROPOSED -> "CONFIRM_SPEC";
                case SPEC_CONFIRMED -> "GENERATE_BUILD_PLAN";
                case BUILD_PLAN_PROPOSED -> "APPROVE_BUILD_PLAN";
                case PLAN_APPROVED -> workflow.getIntentLevel() == IntentLevel.ARCHITECTURE
                        ? "CREATE_CHILD_INTENTS_OR_CONFIRM_BASELINE" : "CREATE_TASKS";
                case TASKS_READY, IN_PROGRESS -> "CONTINUE_TASKS";
                case DELIVERY_SUBMITTED, CI_RUNNING -> "WAIT_FOR_CI";
                case CI_PASSED -> "VERIFY_ALL_TASKS";
                case READY_TO_CLOSE -> "LEADER_CLOSE_WORKFLOW";
                case DONE, CANCELLED, FAILED -> "NONE";
            };
        }
    }

    public record DocumentResponse(
            Long id,
            Long workflowId,
            DocumentType documentType,
            int versionNo,
            String content,
            DocumentFormat contentFormat,
            DocumentSource source,
            Long createdBy,
            Long agentRunId,
            Long codeContextVersionId,
            boolean confirmed,
            Long confirmedBy,
            Instant confirmedAt,
            Instant createdAt) {
        public static DocumentResponse from(DocumentVersion document) {
            return new DocumentResponse(document.getId(), document.getWorkflowId(), document.getDocumentType(),
                    document.getVersionNo(), document.getContent(), document.getContentFormat(), document.getSource(),
                    document.getCreatedBy(), document.getAgentRunId(), document.getCodeContextVersionId(),
                    document.isConfirmed(),
                    document.getConfirmedBy(), document.getConfirmedAt(), document.getCreatedAt());
        }
    }
}
