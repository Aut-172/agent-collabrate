package com.example.agentcollab.dto;

import com.example.agentcollab.domain.WorkflowStatus;

public final class PlanDtos {
    private PlanDtos() {}

    public record CreationResponse(int taskCount, int childIntentCount, WorkflowStatus workflowStatus) {}
}
