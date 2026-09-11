package com.example.agentcollab.client;

import com.example.agentcollab.domain.AgentRunType;
import com.example.agentcollab.domain.DocumentFormat;
import com.example.agentcollab.domain.IntentLevel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile({"test", "mock-provider"})
public class MockAgentProviderClient implements AgentProviderClient {
    private final ObjectMapper json;

    public MockAgentProviderClient(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String providerName() { return "mock"; }

    @Override
    public String modelName() { return "mock-deterministic-v1"; }

    @Override
    public AgentProviderResult generate(AgentGenerationRequest request) {
        if (request.runType() != AgentRunType.GENERATE_CODE_CONTEXT_PLAN && request.codeContext() == null) {
            throw new AgentProviderException("CODE_CONTEXT_MISSING", "正式文档生成缺少 Code Context", false);
        }
        return switch (request.runType()) {
            case GENERATE_CODE_CONTEXT_PLAN -> contextPlan(request);
            case GENERATE_DESIGN -> markdown("Design", request);
            case GENERATE_SPEC -> markdown("Spec", request);
            case GENERATE_BUILD_PLAN -> buildPlan(request);
        };
    }

    private AgentProviderResult contextPlan(AgentGenerationRequest request) {
        if (request.repoInventory() == null) {
            throw new AgentProviderException("REPO_INVENTORY_MISSING", "Context Plan 缺少 Repo Inventory", false);
        }
        ObjectNode root = json.createObjectNode();
        root.put("intentLevel", request.intentLevel().name());
        ObjectNode targets = root.putObject("readTargets");
        ArrayNode files = targets.putArray("files");
        request.repoInventory().inventoryFiles().stream()
                .filter(file -> !"BINARY".equals(file.fileType()))
                .limit(6)
                .forEach(file -> files.addObject().put("path", file.path())
                        .put("reason", "Context Planning Agent 根据 Intent 和 Repo Inventory 选择"));
        targets.putArray("directories");
        targets.putArray("searchQueries");
        root.putArray("expectedEvidence").add("相关代码和配置事实");
        root.putArray("uncertainties");
        try {
            return new AgentProviderResult(json.writeValueAsString(root), DocumentFormat.JSON,
                    "Generated Code Context Plan");
        } catch (JsonProcessingException ex) {
            throw new AgentProviderException("MOCK_SERIALIZATION_FAILED", "Mock Provider 输出序列化失败", false);
        }
    }

    private AgentProviderResult markdown(String documentName, AgentGenerationRequest request) {
        String content = "# " + documentName + ": " + request.title() + "\n\n"
                + "Intent level: " + request.intentLevel() + "\n\n"
                + request.description();
        return new AgentProviderResult(content, DocumentFormat.MARKDOWN,
                "Generated " + documentName + " document");
    }

    private AgentProviderResult buildPlan(AgentGenerationRequest request) {
        ObjectNode root = json.createObjectNode();
        root.put("intentLevel", request.intentLevel().name());
        if (request.intentLevel() == IntentLevel.ARCHITECTURE) {
            root.putArray("architectureGoals").add(request.title());
            root.putArray("systemBoundaries");
            root.putArray("constraints");
            root.putArray("nonFunctionalRequirements");
            root.putArray("childIntents");
            root.putArray("risks");
        } else {
            addStaffingRecommendation(root, request);
        }
        try {
            return new AgentProviderResult(json.writeValueAsString(root), DocumentFormat.JSON,
                    "Generated " + request.intentLevel() + " build plan");
        } catch (JsonProcessingException ex) {
            throw new AgentProviderException("MOCK_SERIALIZATION_FAILED", "Mock Provider 输出序列化失败", false);
        }
    }

    private void addStaffingRecommendation(ObjectNode root, AgentGenerationRequest request) {
        int availableMembers = request.assignableMembers().size();
        int suggestedSize = request.intentLevel() == IntentLevel.FEATURE
                ? Math.min(2, availableMembers) : Math.min(1, availableMembers);
        var selectedMembers = request.assignableMembers().stream().limit(suggestedSize).toList();
        ObjectNode staffing = root.putObject("staffingRecommendation");
        staffing.put("mode", suggestedSize <= 1 ? "SINGLE_OWNER" : "TEAM");
        staffing.put("recommendedTeamSize", suggestedSize);
        String reason = request.intentLevel() == IntentLevel.FEATURE && suggestedSize == 1
                ? "当前仅有一名可分配成员，因此该 Feature 暂采用单人负责"
                : "Mock Provider 根据 Intent 范围、成员画像和当前容量生成的确定性建议";
        staffing.put("reason", reason);
        staffing.put("confidence", 0.5);

        ArrayNode tasks = root.putArray("tasks");
        ArrayNode assignments = root.putArray("assignments");
        for (int index = 0; index < selectedMembers.size(); index++) {
            var member = selectedMembers.get(index);
            String taskKey = "TASK-" + String.format("%03d", index + 1);
            ObjectNode task = tasks.addObject();
            task.put("taskKey", taskKey);
            task.put("title", request.title() + (suggestedSize == 1 ? "" : " - part " + (index + 1)));
            task.put("description", request.description());
            task.put("effortPoints", request.intentLevel() == IntentLevel.CHANGE ? 3 : 5);
            task.put("priority", "MEDIUM");
            task.putArray("scope").add("实现并验证 " + taskKey);
            task.putArray("nonGoals");
            task.putArray("acceptanceCriteria").add(taskKey + " 的验收测试通过");
            task.putArray("verificationCommands").add("mvn test");
            task.putArray("dependencies");
            task.put("branchName", "agent/wf-" + request.workflowId() + "/" + taskKey.toLowerCase());

            ObjectNode assignment = assignments.addObject();
            assignment.put("taskKey", taskKey);
            assignment.put("userId", member.userId());
            assignment.put("projectRole", member.projectRole().name());
            assignment.put("profileVersion", member.profileVersion());
            ObjectNode workload = assignment.putObject("workloadSnapshot");
            workload.put("openEffortPoints", member.openEffortPoints());
            workload.put("weeklyCapacityPoints", member.weeklyCapacityPoints());
            workload.put("availability", member.availability());
            assignment.put("fitReason", "Mock Provider 使用了当前项目成员画像");
            assignment.put("assignmentScore", 0.5);
        }
        root.putArray("alternatives");
        root.putArray("warnings");
    }
}
