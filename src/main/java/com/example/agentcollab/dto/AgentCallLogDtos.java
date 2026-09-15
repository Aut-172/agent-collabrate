package com.example.agentcollab.dto;

import com.example.agentcollab.domain.AgentCallStatus;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AgentCallLogDtos {
    private AgentCallLogDtos() {}

    public record Response(Summary summary, List<Call> records) {}

    public record Summary(
            long totalCalls,
            long succeededCalls,
            long failedCalls,
            long runningCalls,
            long callsWithUsage,
            long inputTokens,
            long outputTokens,
            long reasoningTokens,
            long totalTokens,
            long totalDurationMs,
            Map<String, Long> byRunType) {}

    public record Call(
            Long id,
            Long agentRunId,
            Long workflowId,
            Long taskId,
            int attemptNo,
            String provider,
            String model,
            String runType,
            AgentCallStatus status,
            JsonNode request,
            JsonNode response,
            String errorCode,
            String errorMessage,
            Boolean retryable,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            Long durationMs,
            long inputTokens,
            long outputTokens,
            long reasoningTokens,
            long totalTokens) {}
}
