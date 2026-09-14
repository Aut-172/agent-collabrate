package com.example.agentcollab.service;

import com.example.agentcollab.domain.AgentRunType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentStructureValidatorTest {
    private final DocumentStructureValidator validator = new DocumentStructureValidator();

    @Test
    void acceptsDistinctDesignStructure() {
        String content = """
                # 1. 设计
                ## 1. 决策摘要
                ## 2. 目标与非目标
                ## 3. 当前上下文与约束
                ## 4. 方案架构
                ## 5. 组件职责
                ## 6. 数据流与控制流
                ## 7. 备选方案与权衡
                ## 8. 风险与假设
                ## 9. 待确认决策
                ## 10. 证据引用
                """;
        assertThat(validator.validate(AgentRunType.GENERATE_DESIGN, content)).isEmpty();
    }

    @Test
    void rejectsSpecWithTaskAssignmentContent() {
        String content = """
                # 规格
                ## 1. 范围
                ## 2. 参与者与用例
                ## 3. 行为场景
                ## 4. 接口契约
                ## 5. 数据模型与状态转换
                ## 6. 校验与错误语义
                ## 7. 安全与可观测性
                ## 8. 验收矩阵
                ## 9. 与设计的追踪关系
                ## 10. 证据引用
                ## 11. 任务分配
                """;
        assertThat(validator.validate(AgentRunType.GENERATE_SPEC, content))
                .anyMatch(value -> value.contains("staffing") || value.contains("assignment"));
    }

    @Test
    void reportsMissingSections() {
        assertThat(validator.validate(AgentRunType.GENERATE_DESIGN, "# 设计\n\nshort"))
                .containsExactly("Design 至少需要 5 个结构化 Markdown 章节");
    }

    @Test
    void rejectsEnglishHeadingsAndCornerBracketEvidence() {
        String content = """
                # Design
                ## 决策摘要
                ## 目标与非目标
                ## 当前上下文与约束
                ## 方案架构
                ## 组件职责
                ## 数据流与控制流
                证据：【pom.xml】
                """;
        assertThat(validator.validate(AgentRunType.GENERATE_DESIGN, content))
                .contains("Design 的一级和二级标题必须使用中文")
                .contains("Design 的标题不得包含英文标题文本")
                .contains("Design 的证据引用不得使用【】格式，请改用行内代码路径或 Markdown 链接");
    }

    @Test
    void allowsEnglishIntentTitleAfterChineseDocumentLabel() {
        String content = """
                # 设计：async design
                ## 决策摘要
                ## 目标与非目标
                ## 当前上下文与约束
                ## 方案架构
                ## 组件职责
                ## 数据流与控制流
                ## 备选方案与权衡
                ## 风险与假设
                ## 待确认决策
                ## 证据引用
                """;
        assertThat(validator.validate(AgentRunType.GENERATE_DESIGN, content)).isEmpty();
    }
}
