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

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
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
    void classifiesTransportTimeoutAndDnsFailuresForDiagnostics() {
        assertThat(OpenAiAgentProviderClient.networkErrorType(
                new RuntimeException(new SocketTimeoutException("Read timed out"))))
                .isEqualTo("READ_TIMEOUT");
        assertThat(OpenAiAgentProviderClient.networkErrorType(
                new RuntimeException(new SocketTimeoutException("connect timed out"))))
                .isEqualTo("CONNECT_TIMEOUT");
        assertThat(OpenAiAgentProviderClient.networkErrorType(
                new RuntimeException(new UnknownHostException("api.example.test"))))
                .isEqualTo("DNS_ERROR");
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
        assertThat(prompt).contains("Write all human-readable prose in Simplified Chinese");
        assertThat(prompt).contains("Keep JSON field names, enum values", "shell commands");
        assertThat(prompt).contains("Do not rename fields to buildPlan, estimatedEffortPoints, assigneeUserId");
        assertThat(prompt).contains("Every task must have exactly one matching assignment");
        assertThat(prompt).contains("compare every remaining candidate horizontally", "workloadRatio",
                "MUST NOT override skill", "backend task to a clearly frontend-only member");
    }

    @Test
    void architecturePromptExplicitlyForbidsDevelopmentAssignments() {
        RestClient.Builder builder = RestClient.builder();
        var provider = provider(builder.build(), "test-key", "test-model");

        String prompt = provider.buildPayload(request(AgentRunType.GENERATE_BUILD_PLAN, IntentLevel.ARCHITECTURE))
                .path("input").asText();

        assertThat(prompt).contains("Do not output staffingRecommendation, tasks, assignments");
        assertThat(prompt).contains("complete standalone Workflow intent", "Never emit ARCHITECTURE child intents",
                "create each child as a new Workflow");
    }

    @Test
    void designAndSpecPromptsHaveDistinctDocumentResponsibilities() {
        RestClient.Builder builder = RestClient.builder();
        var provider = provider(builder.build(), "test-key", "test-model");

        String design = provider.buildPayload(request(AgentRunType.GENERATE_DESIGN, IntentLevel.FEATURE))
                .path("input").asText();
        String spec = provider.buildPayload(request(AgentRunType.GENERATE_SPEC, IntentLevel.FEATURE))
                .path("input").asText();

        assertThat(design).contains("文档类型：Design", "方案架构", "备选方案与权衡", "所有一级和二级标题必须使用中文")
                .contains("证据引用使用行内代码路径", "禁止使用中文方括号【】")
                .contains("正文目标 3000-4500 个中文字符", "1-2 段，每段最多 3 句")
                .contains("目标 3-5 条、非目标 2-4 条", "章节预算是上限而不是填充目标")
                .contains("**DEC-001**", "- 问题：", "- 选项：", "- 建议：`OPT-A`", "- 未确认影响：")
                .contains("API 另以 6000 output tokens 为硬上限");
        assertThat(spec).contains("文档类型：Spec", "行为场景", "验收矩阵", "所有一级和二级标题必须使用中文")
                .contains("Do not redesign the architecture")
                .contains("正文目标 3200-4800 个中文字符", "前置条件、触发动作、预期结果各 1 句")
                .contains("最多 12 项", "同一规则只定义一次")
                .contains("**DEC-001**", "每条至少 2 个选项", "无待确认决策")
                .contains("API 另以 6000 output tokens 为硬上限");
        assertThat(design).doesNotContain("文档类型：Spec");
        assertThat(spec).doesNotContain("文档类型：Design");
    }

    @Test
    void promptCarriesResolvedHumanDecisionsAsBindingDownstreamInputs() {
        RestClient.Builder builder = RestClient.builder();
        var provider = provider(builder.build(), "test-key", "test-model");
        var decision = new AgentGenerationRequest.DecisionContext(
                41L, "DESIGN", "DEC-001", "是否允许嵌套？", "OPT-B", "允许无限嵌套");
        var request = new AgentGenerationRequest(1L, AgentRunType.GENERATE_SPEC, IntentLevel.FEATURE,
                "title", "description", "# Design", null, List.of(), null,
                new AgentGenerationRequest.CodeContextInput(3L, 2L, 1L,
                        "1111111111111111111111111111111111111111", json.createObjectNode(),
                        json.createObjectNode(), List.of()), List.of(decision));

        String prompt = provider.buildPayload(request).path("input").asText();

        assertThat(prompt).contains("CONFIRMED HUMAN DECISIONS", "DEC-001", "OPT-B", "允许无限嵌套")
                .contains("binding inputs from prior human confirmation")
                .contains("do not reopen them");
    }

    @Test
    void sendsConfiguredOutputTokenLimitToResponsesApi() {
        RestClient.Builder builder = RestClient.builder();
        var provider = new OpenAiAgentProviderClient(builder.build(), json, "test-key", "test-model",
                "high", true, 6000);

        assertThat(provider.buildPayload(request(AgentRunType.GENERATE_DESIGN, IntentLevel.FEATURE))
                .path("max_output_tokens").asInt()).isEqualTo(6000);
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
