package com.example.agentcollab.client;

import com.example.agentcollab.domain.AgentRunType;
import com.example.agentcollab.domain.DocumentFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Component
@Profile("!test & !mock-provider")
@ConditionalOnProperty(name = "app.agent.provider", havingValue = "openai")
public class OpenAiAgentProviderClient implements AgentProviderClient {
    private final RestClient client;
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;
    private final String reasoningEffort;
    private final boolean disableResponseStorage;
    private final int maxOutputTokens;

    @Autowired
    public OpenAiAgentProviderClient(RestClient.Builder builder, ObjectMapper json,
                                     @Value("${app.agent.api-url:https://api.openai.com/v1/responses}") String apiUrl,
                                     @Value("${app.agent.api-key:}") String apiKey,
                                     @Value("${app.agent.model:}") String model,
                                     @Value("${app.agent.reasoning-effort:}") String reasoningEffort,
                                     @Value("${app.agent.disable-response-storage:true}") boolean disableResponseStorage,
                                     @Value("${app.agent.timeout-seconds:120}") long timeoutSeconds,
                                     @Value("${app.agent.max-output-tokens:12000}") int maxOutputTokens) {
        long boundedTimeoutSeconds = Math.max(1, timeoutSeconds);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(boundedTimeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(boundedTimeoutSeconds));
        this.client = builder.requestFactory(requestFactory).baseUrl(apiUrl).build();
        this.json = json;
        this.apiKey = apiKey;
        this.model = model;
        this.reasoningEffort = reasoningEffort;
        this.disableResponseStorage = disableResponseStorage;
        this.maxOutputTokens = maxOutputTokens;
    }

    OpenAiAgentProviderClient(RestClient client, ObjectMapper json, String apiKey,
                              String model, String reasoningEffort,
                              boolean disableResponseStorage, int maxOutputTokens) {
        this.client = client;
        this.json = json;
        this.apiKey = apiKey;
        this.model = model;
        this.reasoningEffort = reasoningEffort;
        this.disableResponseStorage = disableResponseStorage;
        this.maxOutputTokens = maxOutputTokens;
    }

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public String modelName() {
        return model == null || model.isBlank() ? "unconfigured" : model;
    }

    @Override
    public AgentProviderResult generate(AgentGenerationRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AgentProviderException("AGENT_API_KEY_MISSING",
                    "AGENT_API_KEY is not configured", false);
        }
        if (model == null || model.isBlank()) {
            throw new AgentProviderException("AGENT_MODEL_MISSING",
                    "AGENT_MODEL is not configured", false);
        }

        ObjectNode payload = buildPayload(request);
        ResponseEntity<String> response;
        try {
            response = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);
        } catch (HttpStatusCodeException ex) {
            int status = ex.getStatusCode().value();
            boolean retryable = status == 408 || status == 429 || status >= 500;
            throw new AgentProviderException("OPENAI_HTTP_" + status,
                    "OpenAI Responses API returned HTTP " + status, retryable);
        } catch (ResourceAccessException ex) {
            throw new AgentProviderException("OPENAI_NETWORK_ERROR",
                    "OpenAI Responses API network error: " + rootCauseType(ex), true);
        } catch (RestClientException ex) {
            throw new AgentProviderException("OPENAI_NETWORK_ERROR",
                    "OpenAI Responses API request failed: " + rootCauseType(ex), true);
        }

        String content = extractText(response.getBody());
        if (content.isBlank()) {
            throw new AgentProviderException("OPENAI_EMPTY_RESPONSE",
                    "OpenAI Responses API returned no output text", false);
        }
        DocumentFormat format = request.runType() == AgentRunType.GENERATE_CODE_CONTEXT_PLAN
                || request.runType() == AgentRunType.GENERATE_BUILD_PLAN
                ? DocumentFormat.JSON : DocumentFormat.MARKDOWN;
        return new AgentProviderResult(content, format, "OpenAI Responses API response");
    }

    /** Returns the exact JSON envelope sent to the Responses API, without the Authorization header. */
    public ObjectNode buildPayload(AgentGenerationRequest request) {
        ObjectNode payload = json.createObjectNode();
        payload.put("model", modelName());
        payload.put("input", promptFor(request));
        payload.put("max_output_tokens", maxOutputTokens);
        payload.put("stream", false);
        payload.put("store", !disableResponseStorage ? true : false);
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            payload.putObject("reasoning").put("effort", reasoningEffort);
        }
        return payload;
    }

    private String rootCauseType(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current.getClass().getSimpleName();
    }

    private String promptFor(AgentGenerationRequest request) {
        try {
            String requestJson = json.writeValueAsString(request);
            String outputRule = request.runType() == AgentRunType.GENERATE_CODE_CONTEXT_PLAN
                    || request.runType() == AgentRunType.GENERATE_BUILD_PLAN
                    ? "Return only valid JSON. Do not wrap it in Markdown fences."
                    : "Return the document as Markdown only.";
            return "You are the platform planning agent for an AI collaboration system. "
                    + "Use only the supplied repository evidence and member data. "
                    + "Do not invent repository facts, credentials, or provider results. "
                    + "Write all human-readable prose in Simplified Chinese. Keep JSON field names, enum values, "
                    + "code identifiers, file paths, shell commands, URLs, and protocol literals unchanged. "
                    + "Do not translate or rename those technical values. "
                    + outputRule + "\n\n" + outputContract(request) + "\n\nREQUEST:\n" + requestJson;
        } catch (JsonProcessingException ex) {
            throw new AgentProviderException("AGENT_REQUEST_SERIALIZATION_FAILED",
                    "Agent request could not be serialized", false);
        }
    }

    /**
     * The backend validators use strict JSON Schemas (additionalProperties=false). Keep the
     * protocol in the prompt as well because some OpenAI-compatible gateways do not implement
     * Responses API structured-output controls consistently.
     */
    private String outputContract(AgentGenerationRequest request) {
        return switch (request.runType()) {
            case GENERATE_CODE_CONTEXT_PLAN -> "OUTPUT CONTRACT (Context Plan):\n"
                    + "Return exactly an object with only these fields: "
                    + "intentLevel, readTargets, expectedEvidence, uncertainties. "
                    + "readTargets must contain files (objects with path and reason), directories "
                    + "(objects with path and reason), and searchQueries (strings).";
            case GENERATE_BUILD_PLAN -> buildPlanContract(request.intentLevel());
            case GENERATE_DESIGN, GENERATE_SPEC -> "OUTPUT CONTRACT: Return only a Markdown document. "
                    + "Do not return JSON, a JSON envelope, or Markdown code fences.";
        };
    }

    private String buildPlanContract(com.example.agentcollab.domain.IntentLevel level) {
        if (level == com.example.agentcollab.domain.IntentLevel.ARCHITECTURE) {
            return "OUTPUT CONTRACT (Architecture Build Plan):\n"
                    + "Return exactly this JSON shape and no other fields: "
                    + "{intentLevel:'ARCHITECTURE', architectureGoals:string[], systemBoundaries:string[], "
                    + "constraints:string[], nonFunctionalRequirements:string[], childIntents:[{title,description,intentLevel}], risks:string[]}. "
                    + "Do not output staffingRecommendation, tasks, assignments, taskAssignments, assignee, teamSize, "
                    + "or any development ownership fields.";
        }
        return "OUTPUT CONTRACT (Feature/Change Build Plan):\n"
                + "Return exactly an object with only these top-level fields: "
                + "intentLevel, staffingRecommendation, tasks, assignments, alternatives, warnings. "
                + "staffingRecommendation={mode (SINGLE_OWNER|PAIR|TEAM), recommendedTeamSize (integer), reason (string), confidence (number 0..1)}. "
                + "Each tasks item must use exactly: taskKey, title, description, effortPoints (integer 1..8), priority, "
                + "scope (non-empty string array), nonGoals (string array), acceptanceCriteria (non-empty string array), "
                + "verificationCommands (non-empty string array), dependencies (string array), branchName. "
                + "Each assignments item must use exactly: taskKey, userId (integer), projectRole (LEADER|MEMBER), "
                + "profileVersion (integer), workloadSnapshot={openEffortPoints,weeklyCapacityPoints,availability}, "
                + "fitReason, assignmentScore (number 0..1). "
                + "Every task must have exactly one matching assignment. "
                + "Do not rename fields to buildPlan, estimatedEffortPoints, assigneeUserId, assigneeRationale, "
                + "deliverables, or effortSummary; do not add extra fields.";
    }

    private String extractText(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            JsonNode root = json.readTree(body);
            JsonNode direct = root.path("output_text");
            if (direct.isTextual() && !direct.asText().isBlank()) return direct.asText();
            StringBuilder result = new StringBuilder();
            root.path("output").forEach(item -> item.path("content").forEach(content -> {
                JsonNode text = content.path("text");
                if (text.isTextual()) result.append(text.asText());
            }));
            return result.toString();
        } catch (JsonProcessingException ex) {
            throw new AgentProviderException("OPENAI_INVALID_RESPONSE",
                    "OpenAI Responses API returned invalid JSON", false);
        }
    }
}
