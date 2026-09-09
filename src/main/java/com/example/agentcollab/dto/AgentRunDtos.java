package com.example.agentcollab.dto;

import com.example.agentcollab.domain.AgentRun;
import com.example.agentcollab.domain.AgentRunStatus;
import com.example.agentcollab.domain.AgentRunType;
import java.time.Instant;

public final class AgentRunDtos {
    private AgentRunDtos() {}

    public record EnqueuedRunResponse(Long runId, AgentRunStatus status, String statusUrl) {
        public static EnqueuedRunResponse from(AgentRun run) {
            return new EnqueuedRunResponse(run.getId(), run.getStatus(), "/api/agent-runs/" + run.getId());
        }
    }

    public record AgentRunResponse(
            Long id,
            Long workflowId,
            Long taskId,
            AgentRunType runType,
            String provider,
            String model,
            AgentRunStatus status,
            String requestSummary,
            String responseSummary,
            int retryCount,
            String errorCode,
            String errorMessage,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt) {
        public static AgentRunResponse from(AgentRun run) {
            return new AgentRunResponse(run.getId(), run.getWorkflowId(), run.getTaskId(), run.getRunType(),
                    run.getProvider(), run.getModel(), run.getStatus(), run.getRequestSummary(),
                    run.getResponseSummary(), run.getRetryCount(), run.getErrorCode(), run.getErrorMessage(),
                    run.getCreatedAt(), run.getStartedAt(), run.getFinishedAt());
        }
    }
}
