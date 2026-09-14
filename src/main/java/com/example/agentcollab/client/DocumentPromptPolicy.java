package com.example.agentcollab.client;

import com.example.agentcollab.domain.AgentRunType;
import com.example.agentcollab.domain.IntentLevel;

/**
 * Prompt contract for human-readable planning documents. Keeping the role contract
 * separate from the transport client prevents Design and Spec from collapsing into
 * two differently titled summaries.
 */
final class DocumentPromptPolicy {
    private DocumentPromptPolicy() {}

    static String forRun(AgentRunType runType, IntentLevel level) {
        return switch (runType) {
            case GENERATE_DESIGN -> design(level);
            case GENERATE_SPEC -> spec(level);
            default -> "";
        };
    }

    private static String design(IntentLevel level) {
        return "文档类型：Design（设计决策文档）\n"
                + "为 " + level + " Intent 生成架构/设计决策文档。\n"
                + "回答为什么做、边界是什么、选择了什么方案以及方案如何取舍。\n"
                + "总量预算：正文目标 3000-4500 个中文字符，绝对不要超过 5000 个中文字符；API 另以 6000 output tokens 为硬上限。字数不足时不要补写背景，接近上限时优先保留决策、边界、取舍和证据。\n"
                + "所有一级和二级标题必须使用中文，不得使用 Decision Summary、Goals 等英文标题；可保留章节编号。\n"
                + "必须严格使用以下章节和分节预算，不得新增同级章节：\n"
                + "1. 决策摘要：180-260 字，1-2 段，每段最多 3 句。\n"
                + "2. 目标与非目标：目标 3-5 条、非目标 2-4 条，每条仅 1 句。\n"
                + "3. 当前上下文与约束：250-400 字，最多 6 条，每条最多 2 句。\n"
                + "4. 方案架构：600-850 字，最多 4 个二级小节，每小节最多 2 段、每段最多 3 句。\n"
                + "5. 组件职责：3-6 条，每条最多 2 句；相邻组件的共同事实只写一次。\n"
                + "6. 数据流与控制流：350-550 字，使用 4-7 个有序步骤，每步最多 2 句。\n"
                + "7. 备选方案与权衡：最多 3 个方案，每个方案最多 3 句，必须给出取舍结论。\n"
                + "8. 风险与假设：风险 3-6 条、假设 2-5 条，每条仅 1 句。\n"
                + "9. 待确认决策：最多 5 条；每条必须使用唯一编号 DEC-001、DEC-002……，并依次写出“问题”“可选项（至少 2 个）”“建议”“未确认影响”，最多 4 句；没有待确认项时仅写“无待确认决策”。\n"
                + "10. 证据引用：最多 12 条，每条仅写路径和它支撑的结论，不复述正文。\n"
                + "执行规则：章节预算是上限而不是填充目标；同一仓库事实最多解释一次，后续只引用；除方案架构外，不使用超过二级的标题。\n"
                + "Design 必须引用所提供的 Code Context 证据来说明仓库事实。\n"
                + "证据引用使用行内代码路径（例如：证据：`pom.xml`、`PROJECT_DOCUMENTATION.md`）或标准 Markdown 链接；禁止使用中文方括号【】。\n"
                + "Do not specify detailed API fields, SQL/migrations, file-by-file edits, Tasks, assignments, or branch names.\n"
                + "Do not turn implementation steps into architecture decisions. Mark unknowns as assumptions or open decisions; never present them as facts.";
    }

    private static String spec(IntentLevel level) {
        return "文档类型：Spec（行为规格文档）\n"
                + "基于已确认的 Design，为 " + level + " Intent 生成可实施的行为规格。\n"
                + "回答系统必须表现出什么行为、契约是什么以及如何观察和验证。\n"
                + "总量预算：正文目标 3200-4800 个中文字符，绝对不要超过 5200 个中文字符；API 另以 6000 output tokens 为硬上限。字数不足时不要补写背景，接近上限时优先保留可观察规则、错误语义和验收映射。\n"
                + "所有一级和二级标题必须使用中文，不得使用 Scope、Behavior 等英文标题；可保留章节编号。\n"
                + "必须严格使用以下章节和分节预算，不得新增同级章节：\n"
                + "1. 范围：180-280 字，1-2 段，每段最多 3 句。\n"
                + "2. 参与者与用例：2-6 个用例，每个用例最多 2 句。\n"
                + "3. 行为场景：4-10 个场景；每个场景仅写前置条件、触发动作、预期结果各 1 句。\n"
                + "4. 接口、事件与命令契约：最多 8 项，每项最多 3 句；只定义实现和验收所需的契约。\n"
                + "5. 数据模型与状态转换：最多 8 个字段或状态规则，每条最多 2 句。\n"
                + "6. 校验与错误语义：最多 10 条，每条仅 1 句并包含触发条件和可观察结果。\n"
                + "7. 安全与可观测性：安全 2-5 条、可观测性 2-5 条，每条仅 1 句。\n"
                + "8. 非功能要求：最多 6 条，每条最多 2 句且必须可验证。\n"
                + "9. 验收矩阵：最多 12 项，每项仅包含规则、验收方式和预期结果。\n"
                + "10. 与设计的追踪关系：最多 8 条，每条仅说明设计决策与规格规则的对应关系。\n"
                + "11. 待确认决策：最多 5 条；每条必须使用唯一编号 DEC-001、DEC-002……，并依次写出“问题”“可选项（至少 2 个）”“建议”“未确认影响”，最多 4 句；没有待确认项时仅写“无待确认决策”。\n"
                + "12. 证据引用：最多 12 条，每条仅写路径和它支撑的规则，不复述正文。\n"
                + "执行规则：章节预算是上限而不是填充目标；同一规则只定义一次，其他章节用编号引用；除行为场景外，不为单项内容创建三级标题。\n"
                + "Every externally observable rule must have a concrete acceptance or test implication.\n"
                + "证据引用使用行内代码路径或标准 Markdown 链接；禁止使用中文方括号【】。\n"
                + "Do not redesign the architecture, reopen confirmed Design boundaries, generate Tasks, assignments, staffing, or branch names.\n"
                + "Distinguish existing contracts from proposed additions and unresolved decisions; do not invent repository facts.";
    }
}
