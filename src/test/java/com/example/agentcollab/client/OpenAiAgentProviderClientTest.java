package com.example.agentcollab.client;

import com.example.agentcollab.domain.AgentRunType;
import com.example.agentcollab.domain.DocumentFormat;
import com.example.agentcollab.domain.IntentLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class OpenAiAgentProviderClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private MockRestServiceServer server;

    @AfterEach
    void verifyRequests() {
        if (server != null) server.verify();
    }

    @Test
    void sendsStructuredRequestAndExtractsTopLevelOutputText() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.example.test/v1/responses");
        server = MockRestServiceServer.bindTo(builder).build();
        var provider = provider(builder.build(), "test-key", "test-model");
        var request = request(AgentRunType.GENERATE_BUILD_PLAN, IntentLevel.FEATURE);

        server.expect(requestTo("https://api.example.test/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().contentType("application/json"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"model\":\"test-model\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("REQUEST:")))
                .andRespond(withSuccess("{\"output_text\":\"{\\\"intentLevel\\\":\\\"FEATURE\\\"}\"}",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        AgentProviderResult result = provider.generate(request);

        assertThat(result.content()).isEqualTo("{\"intentLevel\":\"FEATURE\"}");
        assertThat(result.format()).isEqualTo(DocumentFormat.JSON);
    }

    @Test
    void extractsOutputMessageContentWhenConvenienceFieldIsMissing() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.example.test/v1/responses");
        server = MockRestServiceServer.bindTo(builder).build();
        var provider = provider(builder.build(), "test-key", "test-model");
        server.expect(requestTo("https://api.example.test/v1/responses"))
                .andRespond(withSuccess("{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"# Design\\nhello\"}]}]}",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        AgentProviderResult result = provider.generate(request(AgentRunType.GENERATE_DESIGN, IntentLevel.FEATURE));

        assertThat(result.content()).isEqualTo("# Design\nhello");
        assertThat(result.format()).isEqualTo(DocumentFormat.MARKDOWN);
    }

    @Test
    void mapsServerErrorsToRetryableProviderErrorsWithoutLeakingResponseBody() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.example.test/v1/responses");
        server = MockRestServiceServer.bindTo(builder).build();
        var provider = provider(builder.build(), "test-key", "test-model");
        server.expect(requestTo("https://api.example.test/v1/responses"))
                .andRespond(withServerError().body("secret-looking provider details"));

        assertThatThrownBy(() -> provider.generate(request(AgentRunType.GENERATE_SPEC, IntentLevel.FEATURE)))
                .isInstanceOf(AgentProviderException.class)
                .satisfies(error -> {
                    var providerError = (AgentProviderException) error;
                    assertThat(providerError.getCode()).isEqualTo("OPENAI_HTTP_500");
                    assertThat(providerError.isRetryable()).isTrue();
                    assertThat(providerError.getMessage()).doesNotContain("secret-looking");
                });
    }

    @Test
    void rejectsMissingCredentialsBeforeMakingHttpRequest() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.example.test/v1/responses");
        server = MockRestServiceServer.bindTo(builder).build();
        var provider = provider(builder.build(), "", "test-model");

        assertThatThrownBy(() -> provider.generate(request(AgentRunType.GENERATE_SPEC, IntentLevel.FEATURE)))
                .isInstanceOf(AgentProviderException.class)
                .extracting(error -> ((AgentProviderException) error).getCode())
                .isEqualTo("AGENT_API_KEY_MISSING");
    }

    @Test
    void promptPinsBackendBuildPlanFieldNamesAndRejectsAlternativeShape() {
        RestClient.Builder builder = RestClient.builder();
        var provider = provider(builder.build(), "test-key", "test-model");

        String prompt = provider.buildPayload(request(AgentRunType.GENERATE_BUILD_PLAN, IntentLevel.FEATURE))
                .path("input").asText();

        assertThat(prompt).contains("staffingRecommendation", "recommendedTeamSize", "effortPoints",
                "verificationCommands", "fitReason", "assignmentScore");
        assertThat(prompt).contains("Do not rename fields to buildPlan, estimatedEffortPoints, assigneeUserId");
        assertThat(prompt).contains("Every task must have exactly one matching assignment");
    }

    @Test
    void architecturePromptExplicitlyForbidsDevelopmentAssignments() {
        RestClient.Builder builder = RestClient.builder();
        var provider = provider(builder.build(), "test-key", "test-model");

        String prompt = provider.buildPayload(request(AgentRunType.GENERATE_BUILD_PLAN, IntentLevel.ARCHITECTURE))
                .path("input").asText();

        assertThat(prompt).contains("Do not output staffingRecommendation, tasks, assignments");
    }

    private OpenAiAgentProviderClient provider(RestClient client, String key, String model) {
        return new OpenAiAgentProviderClient(client, json, key, model, "xhigh", true, 1000);
    }

    private AgentGenerationRequest request(AgentRunType type, IntentLevel level) {
        var context = new AgentGenerationRequest.CodeContextInput(3L, 2L, 1L,
                "1111111111111111111111111111111111111111", json.createObjectNode(),
                json.createObjectNode(), List.of());
        return new AgentGenerationRequest(1L, type, level, "title", "description", null, null,
                List.of(), null, context);
    }
}
