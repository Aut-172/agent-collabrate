package com.example.agentcollab.service;

import com.example.agentcollab.domain.IntentLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuildPlanValidatorTest {
    private final ObjectMapper json = new ObjectMapper();
    private final BuildPlanValidator validator = new BuildPlanValidator(json);

    @Test
    void acceptsValidTaskReferences() throws Exception {
        JsonNode result = validator.validate(validPlan().toString(), IntentLevel.FEATURE);

        assertThat(result.path("tasks")).hasSize(2);
        assertThat(result.path("assignments")).hasSize(2);
    }

    @Test
    void rejectsDuplicateTaskKeys() throws Exception {
        ObjectNode plan = validPlan();
        ((ObjectNode) plan.withArray("tasks").get(1)).put("taskKey", "api");

        assertInvalid(plan, "重复 taskKey");
    }

    @Test
    void rejectsMissingAndSelfDependencies() throws Exception {
        ObjectNode missing = validPlan();
        ((ObjectNode) missing.withArray("tasks").get(1)).withArray("dependencies").add("missing");
        assertInvalid(missing, "无效任务依赖");

        ObjectNode self = validPlan();
        ((ObjectNode) self.withArray("tasks").get(1)).withArray("dependencies").add("ui");
        assertInvalid(self, "无效任务依赖");
    }

    @Test
    void rejectsTasksWithoutExactlyOneAssignment() throws Exception {
        ObjectNode missing = validPlan();
        missing.withArray("assignments").remove(1);
        assertInvalid(missing, "有且仅有一个负责人建议");

        ObjectNode duplicate = validPlan();
        duplicate.withArray("assignments").add(duplicate.withArray("assignments").get(0).deepCopy());
        assertInvalid(duplicate, "任务分配引用无效");
    }

    private void assertInvalid(JsonNode plan, String message) {
        assertThatThrownBy(() -> validator.validate(plan.toString(), IntentLevel.FEATURE))
                .isInstanceOf(BuildPlanValidationException.class)
                .hasMessageContaining(message);
    }

    private ObjectNode validPlan() throws Exception {
        ObjectNode plan = (ObjectNode) json.readTree("""
                {
                  "intentLevel": "FEATURE",
                  "staffingRecommendation": {
                    "mode": "PAIR",
                    "recommendedTeamSize": 2,
                    "reason": "API and UI work require separate owners",
                    "confidence": 0.9
                  },
                  "tasks": [],
                  "assignments": [],
                  "alternatives": [],
                  "warnings": []
                }
                """);
        plan.withArray("tasks").add(task("api", json.createArrayNode()));
        plan.withArray("tasks").add(task("ui", json.createArrayNode().add("api")));
        plan.withArray("assignments").add(assignment("api", 1));
        plan.withArray("assignments").add(assignment("ui", 2));
        return plan;
    }

    private ObjectNode task(String key, ArrayNode dependencies) {
        ObjectNode task = json.createObjectNode();
        task.put("taskKey", key);
        task.put("title", key + " task");
        task.put("description", "Implement " + key);
        task.put("effortPoints", 3);
        task.put("priority", "HIGH");
        task.putArray("scope").add("Implement required behavior");
        task.putArray("nonGoals");
        task.putArray("acceptanceCriteria").add("Behavior is verified");
        task.putArray("verificationCommands").add("mvn test");
        task.set("dependencies", dependencies);
        task.put("branchName", "feature/" + key);
        return task;
    }

    private ObjectNode assignment(String taskKey, long userId) {
        ObjectNode assignment = json.createObjectNode();
        assignment.put("taskKey", taskKey);
        assignment.put("userId", userId);
        assignment.put("projectRole", userId == 1 ? "LEADER" : "MEMBER");
        assignment.put("profileVersion", 1);
        assignment.putObject("workloadSnapshot")
                .put("openEffortPoints", 0)
                .put("weeklyCapacityPoints", 13)
                .put("availability", "PART_TIME");
        assignment.put("fitReason", "Capability matches the task");
        assignment.put("assignmentScore", 0.85);
        return assignment;
    }
}
