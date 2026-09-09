package com.example.agentcollab.service;

import com.example.agentcollab.client.AgentGenerationRequest;
import com.example.agentcollab.client.AgentProviderException;
import com.example.agentcollab.client.AgentProviderResult;
import com.example.agentcollab.domain.AgentRunType;
import com.example.agentcollab.domain.DocumentFormat;
import com.example.agentcollab.domain.IntentLevel;
import org.springframework.stereotype.Component;

@Component
public class AgentOutputValidator {
    private final BuildPlanValidator buildPlans;

    public AgentOutputValidator(BuildPlanValidator buildPlans) {
        this.buildPlans = buildPlans;
    }

    public void validate(AgentGenerationRequest request, AgentProviderResult result) {
        if (result == null || result.content() == null || result.content().isBlank()) {
            throw invalid("Agent 返回空内容");
        }
        if (request.runType() == AgentRunType.GENERATE_BUILD_PLAN) {
            validateBuildPlan(request.intentLevel(), result);
        } else if (result.format() != DocumentFormat.MARKDOWN) {
            throw invalid("Design 和 Spec 必须使用 Markdown 格式");
        }
    }

    private void validateBuildPlan(IntentLevel level, AgentProviderResult result) {
        if (result.format() != DocumentFormat.JSON) throw invalid("Build Plan 必须使用 JSON 格式");
        try {
            buildPlans.validate(result.content(), level);
        } catch (BuildPlanValidationException ex) {
            throw invalid(ex.getMessage());
        }
    }

    private AgentProviderException invalid(String message) {
        return new AgentProviderException("INVALID_AGENT_OUTPUT", message, false);
    }
}
