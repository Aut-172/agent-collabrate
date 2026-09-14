package com.example.agentcollab.dto;

import com.example.agentcollab.domain.WorkflowStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class PlanDtos {
    private PlanDtos() {}

    public record CreationResponse(int taskCount, int childIntentCount, WorkflowStatus workflowStatus) {}
    public record GranularityWarningResponse(String code, String message, List<String> taskKeys, String suggestedAction) {}
    public record GranularityResponse(int versionNo, List<GranularityWarningResponse> warnings) {}
    public record TaskMergeRequest(@NotBlank String operation, @Size(min = 1, max = 100) List<@NotBlank String> taskKeys,
                                   String reason) {}
}
