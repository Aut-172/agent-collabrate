package com.example.agentcollab.service;

import com.example.agentcollab.domain.AgentRunStatus;
import com.example.agentcollab.domain.OutboxJobType;
import com.example.agentcollab.repository.AgentRunRepository;
import com.example.agentcollab.repository.OutboxJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class AgentRunCancellationService {
    private static final List<AgentRunStatus> ACTIVE_STATUSES =
            List.of(AgentRunStatus.QUEUED, AgentRunStatus.RUNNING);
    private final AgentRunRepository runs;
    private final OutboxJobRepository jobs;

    public AgentRunCancellationService(AgentRunRepository runs, OutboxJobRepository jobs) {
        this.runs = runs;
        this.jobs = jobs;
    }

    @Transactional
    public void cancelForWorkflow(Long workflowId) {
        for (var visibleRun : runs.findByWorkflowIdAndStatusIn(workflowId, ACTIVE_STATUSES)) {
            jobs.findByReferenceForUpdate(OutboxJobType.AGENT_RUN, visibleRun.getId()).ifPresent(job -> {
                if (job.isActive()) job.fail("Workflow cancelled");
            });
            var run = runs.findByIdForUpdate(visibleRun.getId()).orElseThrow();
            if (run.isActive()) run.cancel();
        }
    }
}
