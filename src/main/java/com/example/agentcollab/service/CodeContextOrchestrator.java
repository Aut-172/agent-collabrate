package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CodeContextOrchestrator {
    private final ContextPlanValidator validator;
    private final CodeContextPlanRepository plans;
    private final CodeContextRunRepository contextRuns;
    private final AgentRunRepository agentRuns;
    private final WorkflowRepository workflows;
    private final OutboxJobRepository jobs;

    public CodeContextOrchestrator(ContextPlanValidator validator, CodeContextPlanRepository plans,
                                   CodeContextRunRepository contextRuns, AgentRunRepository agentRuns,
                                   WorkflowRepository workflows, OutboxJobRepository jobs) {
        this.validator = validator; this.plans = plans; this.contextRuns = contextRuns;
        this.agentRuns = agentRuns; this.workflows = workflows; this.jobs = jobs;
    }

    @Transactional
    public CodeContextPlan recordPlan(Long agentRunId, String content) {
        AgentRun run = agentRuns.findByIdForUpdate(agentRunId).orElseThrow();
        Workflow workflow = workflows.findById(run.getWorkflowId()).orElseThrow();
        JsonNode planJson = validator.validate(content);
        if (!workflow.getIntentLevel().name().equals(planJson.path("intentLevel").asText())) {
            throw new IllegalArgumentException("Context Plan intentLevel 与 Workflow 不一致");
        }
        CodeContextPlan plan = plans.save(new CodeContextPlan(workflow.getProjectId(), workflow.getId(),
                run.getInventoryVersionId(), run.getId(), planJson));
        run.bindContextPlan(plan.getId());
        CodeContextRun evidenceRun = contextRuns.save(
                CodeContextRun.evidenceCollection(workflow.getProjectId(), null, plan.getId()));
        jobs.save(new OutboxJob(OutboxJobType.CODE_CONTEXT_EVIDENCE, evidenceRun.getId()));
        return plan;
    }
}
