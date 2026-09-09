package com.example.agentcollab.service;

import com.example.agentcollab.domain.IntentLevel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.Set;

@Component
public class BuildPlanValidator {
    private final ObjectMapper json;
    private final JsonSchema schema;

    public BuildPlanValidator(ObjectMapper json) {
        this.json = json;
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
        if (root == null || !schema.validate(root).isEmpty()) {
            throw invalid("Build Plan 不符合 Intent 对应的 JSON Schema");
        }
        if (!expectedLevel.name().equals(root.path("intentLevel").asText())) {
            throw invalid("Build Plan 的 Intent 层级不匹配");
        }
        if (expectedLevel != IntentLevel.ARCHITECTURE) validateTaskReferences(root);
        return root;
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
}
