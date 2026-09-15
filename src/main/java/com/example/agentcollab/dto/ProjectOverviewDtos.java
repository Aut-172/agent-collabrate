package com.example.agentcollab.dto;

import com.example.agentcollab.domain.IntentLevel;
import com.example.agentcollab.domain.ProjectCiStatus;
import com.example.agentcollab.domain.TaskStatus;
import com.example.agentcollab.domain.WorkflowStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ProjectOverviewDtos {
    private ProjectOverviewDtos() {}

    public record OverviewResponse(
            LocalDate from,
            LocalDate to,
            ProjectSummary project,
            WorkflowStats workflows,
            TaskStats tasks,
            List<MemberWorkload> members,
            TokenStats tokens) {}

    public record ProjectSummary(
            Long projectId,
            Instant createdAt,
            ProjectCiStatus ciStatus,
            long activeWorkflowCount,
            long openTaskCount,
            int openEffortPoints) {}

    public record WorkflowStats(
            long total,
            Map<WorkflowStatus, Long> byStatus,
            Map<IntentLevel, Long> byIntentLevel,
            List<DailyCount> dailyCreated) {}

    public record TaskStats(
            long total,
            Map<TaskStatus, Long> byStatus,
            long openTaskCount,
            int openEffortPoints,
            List<DailyCount> dailyCompleted) {}

    public record MemberWorkload(
            Long userId,
            String username,
            String projectRole,
            int assignedTaskCount,
            int openTaskCount,
            int openEffortPoints,
            int completedTaskCount,
            Integer weeklyCapacityPoints,
            String availability,
            Double workloadRatio) {}

    public record DailyCount(LocalDate date, long count) {}

    public record TokenStats(
            long callCount,
            long callsWithUsage,
            long inputTokens,
            long outputTokens,
            long reasoningTokens,
            long totalTokens) {}
}
