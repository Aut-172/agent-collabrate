package com.example.agentcollab.service;

import com.example.agentcollab.domain.IntentLevel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class BuildPlanValidator {
    private static final Logger log = LoggerFactory.getLogger(BuildPlanValidator.class);
    private final ObjectMapper json;
    private final JsonSchema schema;
    private final TaskGranularityValidator granularity;

    public BuildPlanValidator(ObjectMapper json) {
        this(json, new TaskGranularityValidator());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public BuildPlanValidator(ObjectMapper json, TaskGranularityValidator granularity) {
        this.json = json;
        this.granularity = granularity;
        this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(getClass().getResourceAsStream("/schema/build-plan-v1.schema.json"));
    }

    public JsonNode validate(String content, IntentLevel expectedLevel) {
        JsonNode root;
        try {
            root = json.readTree(content);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw invalid("Build Plan 不是有效 JSON");
        }
        if (root == null) {
            throw invalid("Build Plan 不符合 Intent 对应的 JSON Schema：根对象不能为空");
        }
        Set<ValidationMessage> schemaErrors = schema.validate(root);
        if (!schemaErrors.isEmpty()) {
            String details = schemaErrors.stream()
                    .sorted(Comparator.comparing(error -> error.getInstanceLocation().toString()))
                    .limit(5)
                    .map(this::formatSchemaError)
                    .collect(Collectors.joining("；"));
            String requiredField = firstMissingRequiredField(root, expectedLevel);
            if (requiredField != null) details = requiredField + "；" + details;
            throw invalid("Build Plan 不符合 Intent 对应的 JSON Schema：" + details);
        }
        if (!expectedLevel.name().equals(root.path("intentLevel").asText())) {
            throw invalid("Build Plan 的 Intent 层级不匹配");
        }
        if (expectedLevel == IntentLevel.ARCHITECTURE) {
            validateArchitectureChildren(root);
        } else {
            validateTaskReferences(root);
            granularity.warnings(root, expectedLevel).forEach(warning -> log.atWarn()
                    .setMessage("Build Plan task granularity warning")
                    .addKeyValue("intentLevel", expectedLevel.name())
                    .addKeyValue("warning", warning)
                    .log());
        }
        return root;
    }

    private void validateArchitectureChildren(JsonNode root) {
        for (JsonNode child : root.path("childIntents")) {
            if (child.path("intentLevel").asText().equals(IntentLevel.ARCHITECTURE.name())) {
                throw invalid("Architecture 只能创建 Feature 或 Change 子 Intent，不能嵌套 Architecture");
            }
        }
    }

    private void validateTaskReferences(JsonNode root) {
        Set<String> taskKeys = new HashSet<>();
        for (JsonNode task : root.path("tasks")) {
            String key = task.path("taskKey").asText();
            if (!taskKeys.add(key)) throw invalid("Build Plan 包含重复 taskKey");
        }
        for (JsonNode task : root.path("tasks")) {
            String key = task.path("taskKey").asText();
            for (JsonNode dependency : task.path("dependencies")) {
                String dependencyKey = dependency.asText();
                if (key.equals(dependencyKey) || !taskKeys.contains(dependencyKey)) {
                    throw invalid("Build Plan 包含无效任务依赖");
                }
            }
        }
        Set<String> assignedKeys = new HashSet<>();
        for (JsonNode assignment : root.path("assignments")) {
            String key = assignment.path("taskKey").asText();
            if (!taskKeys.contains(key) || !assignedKeys.add(key)) {
                throw invalid("Build Plan 的任务分配引用无效");
            }
        }
        if (!assignedKeys.equals(taskKeys)) throw invalid("每个开发任务必须有且仅有一个负责人建议");
    }

    private BuildPlanValidationException invalid(String message) {
        return new BuildPlanValidationException(message);
    }

    private String formatSchemaError(ValidationMessage error) {
        String path = error.getInstanceLocation() == null
                ? "$" : error.getInstanceLocation().toString();
        String message = error.getMessage() == null ? "schema validation failed" : error.getMessage();
        if (error.getDetails() != null && !error.getDetails().isEmpty()) {
            message += " (details: " + error.getDetails() + ")";
        }
        message = message.replace('\r', ' ').replace('\n', ' ');
        return path + " " + message;
    }

    private String firstMissingRequiredField(JsonNode root, IntentLevel level) {
        List<String> required = level == IntentLevel.ARCHITECTURE
                ? List.of("intentLevel", "architectureGoals", "systemBoundaries", "constraints",
                "nonFunctionalRequirements", "childIntents", "risks")
                : List.of("intentLevel", "staffingRecommendation", "tasks", "assignments", "alternatives", "warnings");
        for (String field : required) {
            if (!root.has(field)) return "$ missing required field '" + field + "'";
        }
        if (level == IntentLevel.ARCHITECTURE) {
            for (int i = 0; i < root.path("childIntents").size(); i++) {
                JsonNode child = root.path("childIntents").get(i);
                for (String field : List.of("title", "description", "intentLevel")) {
                    if (!child.has(field)) return "$.childIntents[" + i + "] missing required field '" + field + "'";
                }
            }
            return null;
        }
        for (int i = 0; i < root.path("tasks").size(); i++) {
            JsonNode task = root.path("tasks").get(i);
            for (String field : List.of("taskKey", "title", "description", "effortPoints", "priority", "scope",
                    "nonGoals", "acceptanceCriteria", "verificationCommands", "dependencies", "branchName")) {
                if (!task.has(field)) return "$.tasks[" + i + "] missing required field '" + field + "'";
            }
        }
        for (int i = 0; i < root.path("assignments").size(); i++) {
            JsonNode assignment = root.path("assignments").get(i);
            for (String field : List.of("taskKey", "userId", "projectRole", "profileVersion", "workloadSnapshot",
                    "fitReason", "assignmentScore")) {
                if (!assignment.has(field)) return "$.assignments[" + i + "] missing required field '" + field + "'";
            }
        }
        return null;
    }
}
