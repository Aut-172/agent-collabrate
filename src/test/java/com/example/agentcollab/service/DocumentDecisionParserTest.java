package com.example.agentcollab.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentDecisionParserTest {
    private final DocumentDecisionParser parser = new DocumentDecisionParser();

    @Test
    void parsesStrictDecisionBlocks() {
        String markdown = """
                # 设计：评论功能

                ## 9. 待确认决策

                **DEC-001**
                - 问题：回复是否允许嵌套？
                - 选项：
                  - `OPT-A`：只允许一层回复
                  - `OPT-B`：允许无限嵌套
                - 建议：`OPT-A`
                - 未确认影响：无法确定数据关系和展示结构

                ## 10. 证据引用
                - `pom.xml`
                """;

        var decisions = parser.parse(markdown);

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.decisionKey()).isEqualTo("DEC-001");
            assertThat(decision.question()).isEqualTo("回复是否允许嵌套？");
            assertThat(decision.options()).extracting(DocumentDecisionParser.Option::key)
                    .containsExactly("OPT-A", "OPT-B");
            assertThat(decision.recommendedOption()).isEqualTo("OPT-A");
            assertThat(decision.unresolvedImpact()).contains("数据关系");
        });
    }

    @Test
    void acceptsExplicitEmptyDecisionSection() {
        assertThat(parser.parse("## 待确认决策\n\n无待确认决策。\n\n## 证据引用\n"))
                .isEmpty();
    }

    @Test
    void rejectsMissingOptionsAndUnknownRecommendation() {
        String markdown = """
                ## 待确认决策
                **DEC-001**
                - 问题：选择哪种方案？
                - 选项：
                  - `OPT-A`：方案 A
                  - `OPT-B`：方案 B
                - 建议：`OPT-C`
                - 未确认影响：无法继续
                """;

        assertThatThrownBy(() -> parser.parse(markdown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("建议必须引用已有选项编号");
    }

    @Test
    void rejectsDuplicateDecisionKeys() {
        String block = """
                **DEC-001**
                - 问题：选择哪种方案？
                - 选项：
                  - `OPT-A`：方案 A
                  - `OPT-B`：方案 B
                - 建议：`OPT-A`
                - 未确认影响：无法继续
                """;

        assertThatThrownBy(() -> parser.parse("## 待确认决策\n" + block + block))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("编号不能重复");
    }
}
