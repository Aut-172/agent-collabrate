package com.example.agentcollab.service;

import com.example.agentcollab.domain.AgentCallRecord;
import com.example.agentcollab.domain.AgentCallStatus;
import com.example.agentcollab.domain.AgentRun;
import com.example.agentcollab.domain.Project;
import com.example.agentcollab.domain.Workflow;
import com.example.agentcollab.dto.AgentCallLogDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.AgentCallRecordRepository;
import com.example.agentcollab.repository.AgentRunRepository;
import com.example.agentcollab.repository.ProjectRepository;
import com.example.agentcollab.repository.WorkflowRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentCallLogService {
    private final ProjectRepository projects;
    private final ProjectAccessService access;
    private final WorkflowRepository workflows;
    private final AgentRunRepository agentRuns;
    private final AgentCallRecordRepository records;

    public AgentCallLogService(ProjectRepository projects, ProjectAccessService access,
                               WorkflowRepository workflows, AgentRunRepository agentRuns,
                               AgentCallRecordRepository records) {
        this.projects = projects;
        this.access = access;
        this.workflows = workflows;
        this.agentRuns = agentRuns;
        this.records = records;
    }

    @Transactional(readOnly = true)
    public AgentCallLogDtos.Response list(Long actorId, Long projectId) {
        access.requireLeader(projectId, actorId);
        projects.findById(projectId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在"));
        List<Workflow> projectWorkflows = workflows.findByProjectIdOrderByCreatedAtAsc(projectId);
        List<Long> workflowIds = projectWorkflows.stream().map(Workflow::getId).toList();
        List<AgentRun> projectRuns = workflowIds.isEmpty()
                ? List.of() : agentRuns.findByWorkflowIdInOrderByCreatedAtDesc(workflowIds);
        Map<Long, AgentRun> runsById = projectRuns.stream()
                .collect(java.util.stream.Collectors.toMap(AgentRun::getId, value -> value));
        List<Long> runIds = projectRuns.stream().map(AgentRun::getId).toList();
        List<AgentCallRecord> callRecords = runIds.isEmpty()
                ? List.of() : records.findByAgentRunIdInOrderByCreatedAtDesc(runIds);

        long succeeded = callRecords.stream().filter(record -> record.getStatus() == AgentCallStatus.SUCCEEDED).count();
        long failed = callRecords.stream().filter(record -> record.getStatus() == AgentCallStatus.FAILED).count();
        long running = callRecords.stream().filter(record -> record.getStatus() == AgentCallStatus.RUNNING).count();
        long duration = callRecords.stream().mapToLong(record -> record.getDurationMs() == null ? 0 : record.getDurationMs()).sum();
        UsageTotals totals = new UsageTotals();
        Map<String, Long> byRunType = new LinkedHashMap<>();
        List<AgentCallLogDtos.Call> response = callRecords.stream().map(record -> {
            AgentRun run = runsById.get(record.getAgentRunId());
            Usage usage = Usage.from(record.getResponseJson());
            totals.add(usage);
            if (record.getRunType() != null) byRunType.merge(record.getRunType(), 1L, Long::sum);
            return new AgentCallLogDtos.Call(record.getId(), record.getAgentRunId(),
                    run == null ? null : run.getWorkflowId(), run == null ? null : run.getTaskId(),
                    record.getAttemptNo(), record.getProvider(), record.getModel(), record.getRunType(),
                    record.getStatus(), record.getRequestJson(), record.getResponseJson(), record.getErrorCode(),
                    record.getErrorMessage(), record.getRetryable(), record.getCreatedAt(), record.getStartedAt(),
                    record.getFinishedAt(), record.getDurationMs(), usage.inputTokens, usage.outputTokens,
                    usage.reasoningTokens, usage.totalTokens);
        }).toList();
        return new AgentCallLogDtos.Response(new AgentCallLogDtos.Summary(callRecords.size(), succeeded, failed,
                running, totals.callsWithUsage, totals.inputTokens, totals.outputTokens, totals.reasoningTokens,
                totals.totalTokens, duration, byRunType), response);
    }

    private static final class UsageTotals {
        private long callsWithUsage;
        private long inputTokens;
        private long outputTokens;
        private long reasoningTokens;
        private long totalTokens;

        private void add(Usage usage) {
            if (!usage.present) return;
            callsWithUsage++;
            inputTokens += usage.inputTokens;
            outputTokens += usage.outputTokens;
            reasoningTokens += usage.reasoningTokens;
            totalTokens += usage.totalTokens;
        }
    }

    private record Usage(boolean present, long inputTokens, long outputTokens,
                         long reasoningTokens, long totalTokens) {
        private static Usage from(com.fasterxml.jackson.databind.JsonNode response) {
            if (response == null || !response.path("usage").isObject()) return new Usage(false, 0, 0, 0, 0);
            var usage = response.path("usage");
            long input = Math.max(0, usage.path("input_tokens").asLong(0));
            long output = Math.max(0, usage.path("output_tokens").asLong(0));
            long reasoning = Math.max(0, usage.path("output_tokens_details").path("reasoning_tokens").asLong(0));
            long total = usage.path("total_tokens").asLong(-1);
            return new Usage(true, input, output, reasoning, total >= 0 ? total : input + output);
        }
    }
}
