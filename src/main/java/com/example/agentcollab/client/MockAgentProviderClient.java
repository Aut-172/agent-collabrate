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
            case GENERATE_DESIGN -> design(request);
            case GENERATE_SPEC -> spec(request);
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

    private AgentProviderResult design(AgentGenerationRequest request) {
        String content = "# 设计：" + request.title() + "\n\n"
                + "## 决策摘要\n\n围绕 Intent 目标建立清晰的交付边界。\n\n"
                + "## 目标与非目标\n\n- 目标：" + request.description() + "\n- 非目标：不改变无关模块。\n\n"
                + "## 当前上下文与约束\n\n基于当前 Code Context 和仓库约束设计。\n\n"
                + "## 方案架构\n\n采用最小增量方案，复用现有边界。\n\n"
                + "## 组件职责\n\n现有组件保持职责稳定，仅增加本 Intent 所需能力。\n\n"
                + "## 数据流与控制流\n\n请求经过现有入口、领域服务和持久化边界。\n\n"
                + "## 备选方案与权衡\n\n备选方案会增加运行时依赖，暂不采用。\n\n"
                + "## 风险与假设\n\n假设现有部署约束保持不变。\n\n"
                + "## 待确认决策\n\n无待确认决策。\n\n"
                + "## 证据引用\n\n证据：`Code Context` 中提供的仓库事实。";
        return new AgentProviderResult(content, DocumentFormat.MARKDOWN, "Generated Design document");
    }

    private AgentProviderResult spec(AgentGenerationRequest request) {
        String content = "# 规格：" + request.title() + "\n\n"
                + "## 范围\n\n本规格覆盖已确认 Design 的可观察行为。\n\n"
                + "## 参与者与用例\n\n成员触发流程并获得可验证结果。\n\n"
                + "## 行为场景\n\n成功、输入无效和依赖失败场景均需有明确结果。\n\n"
                + "## 接口、事件与命令契约\n\n沿用现有协议格式，新增字段必须显式说明。\n\n"
                + "## 数据模型与状态转换\n\n状态只能按既有状态机规则推进。\n\n"
                + "## 校验与错误语义\n\n无效输入返回稳定错误码，不泄漏敏感信息。\n\n"
                + "## 安全与可观测性\n\n遵循现有认证、审计和结构化日志约定。\n\n"
                + "## 非功能要求\n\n保持现有性能、可靠性和可部署性约束。\n\n"
                + "## 验收矩阵\n\n每个外部行为至少对应一个验收场景。\n\n"
                + "## 与设计的追踪关系\n\n本规格逐项映射已确认 Design 的边界和决策。\n\n"
                + "## 已确认决策\n\n" + confirmedDecisionMarkdown(request) + "\n\n"
                + "## 待确认决策\n\n无待确认决策。\n\n"
                + "## 证据引用\n\n证据：`Code Context` 中提供的仓库事实。";
        return new AgentProviderResult(content, DocumentFormat.MARKDOWN, "Generated Spec document");
    }

    private String confirmedDecisionMarkdown(AgentGenerationRequest request) {
        if (request.resolvedDecisions().isEmpty()) return "无。";
        return request.resolvedDecisions().stream()
                .map(decision -> "- `" + decision.decisionKey() + "`：" + decision.question()
                        + "；最终选择：" + decision.selectedOptionLabel() + " (`"
                        + decision.selectedOption() + "`)")
                .collect(java.util.stream.Collectors.joining("\n"));
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
            task.put("title", request.title() + (suggestedSize == 1 ? "" : " - 子任务 " + (index + 1)));
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
