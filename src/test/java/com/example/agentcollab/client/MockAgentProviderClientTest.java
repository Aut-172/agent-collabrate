package com.example.agentcollab.client;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.service.AgentOutputValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockAgentProviderClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private final MockAgentProviderClient provider = new MockAgentProviderClient(json);
    private final AgentOutputValidator validator = new AgentOutputValidator(
            new com.example.agentcollab.service.BuildPlanValidator(json),
            new com.example.agentcollab.service.ContextPlanValidator(json));

    @Test
    void separatesArchitectureOutputFromProfileBasedFeatureStaffing() throws Exception {
        var member = new AgentGenerationRequest.MemberContext(
                7L, ProjectMember.Role.LEADER, 3, json.createObjectNode().put("summary", "backend"),
                5, 13, "PART_TIME");
        var architectureRequest = request(IntentLevel.ARCHITECTURE, List.of(member));
        AgentProviderResult architecture = provider.generate(architectureRequest);
        validator.validate(architectureRequest, architecture);
        assertThat(json.readTree(architecture.content()).has("staffingRecommendation")).isFalse();
        assertThat(json.readTree(architecture.content()).has("assignments")).isFalse();

        var featureRequest = request(IntentLevel.FEATURE, List.of(member));
        AgentProviderResult feature = provider.generate(featureRequest);
        validator.validate(featureRequest, feature);
        var featureJson = json.readTree(feature.content());
        assertThat(featureJson.path("assignments").get(0).path("userId").asLong()).isEqualTo(7L);
        assertThat(featureJson.path("assignments").get(0).path("workloadSnapshot")
                .path("weeklyCapacityPoints").asInt()).isEqualTo(13);
    }

    @Test
    void rejectsArchitectureOutputContainingDevelopmentAssignments() {
        var request = request(IntentLevel.ARCHITECTURE, List.of());
        var invalid = new AgentProviderResult(
                "{\"intentLevel\":\"ARCHITECTURE\",\"assignments\":[]}",
                DocumentFormat.JSON, "invalid");
        assertThatThrownBy(() -> validator.validate(request, invalid))
                .isInstanceOf(AgentProviderException.class)
                .extracting(error -> ((AgentProviderException) error).getCode())
                .isEqualTo("INVALID_AGENT_OUTPUT");
    }

    @Test
    void featureDefaultsToMoreCollaboratorsThanChangeWhenCapacityIsAvailable() throws Exception {
        var first = new AgentGenerationRequest.MemberContext(
                7L, ProjectMember.Role.LEADER, 1, json.createObjectNode().put("summary", "backend"),
                0, 13, "PART_TIME");
        var second = new AgentGenerationRequest.MemberContext(
                8L, ProjectMember.Role.MEMBER, 2, json.createObjectNode().put("summary", "frontend"),
                0, 20, "PART_TIME");
        var members = List.of(first, second);

        var feature = json.readTree(provider.generate(request(IntentLevel.FEATURE, members)).content());
        var change = json.readTree(provider.generate(request(IntentLevel.CHANGE, members)).content());

        assertThat(feature.path("staffingRecommendation").path("recommendedTeamSize").asInt()).isEqualTo(2);
        assertThat(change.path("staffingRecommendation").path("recommendedTeamSize").asInt()).isEqualTo(1);
    }

    private AgentGenerationRequest request(
            IntentLevel level, List<AgentGenerationRequest.MemberContext> members) {
        return new AgentGenerationRequest(1L, AgentRunType.GENERATE_BUILD_PLAN, level,
                "title", "description", null, null, members, null);
    }
}
