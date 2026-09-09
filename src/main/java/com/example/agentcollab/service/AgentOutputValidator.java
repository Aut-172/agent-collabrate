package com.example.agentcollab.service;

import com.example.agentcollab.client.AgentGenerationRequest;
import com.example.agentcollab.client.AgentProviderException;
import com.example.agentcollab.client.AgentProviderResult;
import com.example.agentcollab.domain.AgentRunType;
import com.example.agentcollab.domain.DocumentFormat;
import com.example.agentcollab.domain.IntentLevel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class AgentOutputValidator {
    private static final List<String> ARCHITECTURE_FORBIDDEN_FIELDS = List.of(
            "suggestedAssignee", "teamSize", "taskAssignments", "staffingRecommendation", "assignments");
    private final ObjectMapper json;

    public AgentOutputValidator(ObjectMapper json) {
        this.json = json;
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
        JsonNode root;
        try {
            root = json.readTree(result.content());
        } catch (JsonProcessingException ex) {
            throw invalid("Build Plan 不是有效 JSON");
        }
        if (!root.isObject() || !level.name().equals(root.path("intentLevel").asText())) {
            throw invalid("Build Plan 的 Intent 层级不匹配");
        }
        if (level == IntentLevel.ARCHITECTURE) {
            if (ARCHITECTURE_FORBIDDEN_FIELDS.stream().anyMatch(root::has)) {
                throw invalid("Architecture Build Plan 包含开发分工字段");
            }
            return;
        }
        JsonNode staffing = root.path("staffingRecommendation");
        if (!staffing.isObject()
                || staffing.path("mode").asText().isBlank()
                || staffing.path("recommendedTeamSize").asInt(0) < 1
                || staffing.path("reason").asText().isBlank()
                || !root.path("assignments").isArray()
                || !root.path("warnings").isArray()) {
            throw invalid("Feature/Change Build Plan 缺少完整分工建议");
        }
    }

    private AgentProviderException invalid(String message) {
        return new AgentProviderException("INVALID_AGENT_OUTPUT", message, false);
    }
}
