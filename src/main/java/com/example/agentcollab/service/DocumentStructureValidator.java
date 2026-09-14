package com.example.agentcollab.service;

import com.example.agentcollab.domain.AgentRunType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Validates the semantic boundary of generated Design and Spec Markdown. */
@Component
public class DocumentStructureValidator {
    private static final Pattern HEADING = Pattern.compile("(?im)^\\s{0,3}#{1,6}\\s+(.+?)\\s*#*\\s*$");
    private static final List<List<String>> DESIGN_SECTIONS = List.of(
            List.of("decision summary", "决策摘要"), List.of("goals", "目标"), List.of("non-goals", "non goals", "非目标"),
            List.of("current context", "上下文"), List.of("constraints", "约束"),
            List.of("proposed architecture", "架构方案", "架构设计"), List.of("component responsibilities", "组件职责"),
            List.of("data and control flow", "数据流", "控制流"), List.of("alternatives", "备选", "权衡", "trade-offs"),
            List.of("risks", "风险"), List.of("assumptions", "假设"), List.of("open decisions", "待确认", "开放决策"),
            List.of("evidence", "证据"));
    private static final List<List<String>> SPEC_SECTIONS = List.of(
            List.of("scope", "范围"), List.of("actors", "参与者"), List.of("use cases", "用例"),
            List.of("behavior", "行为"), List.of("scenarios", "场景"), List.of("api", "event", "command", "接口", "事件", "命令"),
            List.of("contract", "契约"), List.of("data model", "数据模型"), List.of("state transitions", "状态转换"),
            List.of("validation", "校验", "验证"), List.of("error", "错误"), List.of("security", "安全"),
            List.of("observability", "可观测"), List.of("non-functional", "非功能"), List.of("acceptance", "验收"),
            List.of("traceability", "追踪", "可追溯"), List.of("open decisions", "待确认", "开放决策"),
            List.of("evidence", "证据"));
    private final DocumentDecisionParser decisionParser = new DocumentDecisionParser();

    public List<String> validate(AgentRunType type, String content) {
        List<String> headings = new ArrayList<>();
        List<String> rawHeadings = new ArrayList<>();
        var matcher = HEADING.matcher(content == null ? "" : content);
        while (matcher.find()) {
            rawHeadings.add(matcher.group(1).trim());
            headings.add(normalize(matcher.group(1)));
        }
        List<String> formatErrors = new ArrayList<>();
        if (headings.stream().anyMatch(heading -> !heading.matches(".*[\\u4e00-\\u9fff].*"))) {
            formatErrors.add((type == AgentRunType.GENERATE_DESIGN ? "Design" : "Spec")
                    + " 的一级和二级标题必须使用中文");
        }
        if (rawHeadings.stream().anyMatch(this::startsWithEnglishHeadingText)) {
            formatErrors.add((type == AgentRunType.GENERATE_DESIGN ? "Design" : "Spec")
                    + " 的标题不得包含英文标题文本");
        }
        if (content != null && (content.contains("【") || content.contains("】"))) {
            formatErrors.add((type == AgentRunType.GENERATE_DESIGN ? "Design" : "Spec")
                    + " 的证据引用不得使用【】格式，请改用行内代码路径或 Markdown 链接");
        }
        try {
            decisionParser.parse(content);
        } catch (IllegalArgumentException ex) {
            formatErrors.add("待确认决策格式无效：" + ex.getMessage());
        }
        if (!formatErrors.isEmpty()) return formatErrors;
        if (headings.size() < 5) {
            return List.of(type == AgentRunType.GENERATE_DESIGN
                    ? "Design 至少需要 5 个结构化 Markdown 章节"
                    : "Spec 至少需要 5 个结构化 Markdown 章节");
        }
        List<List<String>> required = type == AgentRunType.GENERATE_DESIGN ? DESIGN_SECTIONS : SPEC_SECTIONS;
        long matched = required.stream().filter(section -> section.stream()
                .anyMatch(token -> headings.stream().anyMatch(h -> h.contains(token)))).count();
        if (matched < 6) {
            return List.of((type == AgentRunType.GENERATE_DESIGN ? "Design" : "Spec")
                    + " 必须覆盖至少 6 个文档职责章节，当前仅识别到 " + matched + " 个");
        }
        String headingText = String.join(" ", headings);
        List<String> forbidden = new ArrayList<>();
        if (type == AgentRunType.GENERATE_DESIGN) {
            if (headingText.matches("(?s).*\\b(task|assignment|branchname|branch name|sql|migration)\\b.*")
                    || headingText.matches("(?s).*(任务|分配|分支|负责人|迁移).*")) {
                forbidden.add("Design 不得包含 Task、assignment 或 branchName 分工内容");
            }
        } else if (headingText.matches("(?s).*\\b(staffing|assignment|branchname|branch name|task)\\b.*")
                || headingText.matches("(?s).*(分工|任务分配|负责人|分支名).*")) {
            forbidden.add("Spec 不得包含 staffing、assignment 或 branchName 分工内容");
        }
        return forbidden;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[：:（）()/_-]", " ").trim();
    }

    /**
     * A document title may include the Intent title after a Chinese label, for
     * example "设计：async design". Only the heading label itself must be Chinese.
     */
    private boolean startsWithEnglishHeadingText(String heading) {
        String value = heading == null ? "" : heading.trim()
                .replaceFirst("^\\d+[.、)]\\s*", "");
        return value.matches("^[A-Za-z][A-Za-z0-9 _-]*(?::|：|$).*");
    }
}
