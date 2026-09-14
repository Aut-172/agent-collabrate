package com.example.agentcollab.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class DocumentDecisionParser {
    private static final Pattern SECTION = Pattern.compile(
            "^\\s{0,3}#{1,6}\\s+(?:\\d+[.、]\\s*)?待确认决策\\s*#*\\s*$");
    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}#{1,6}\\s+.*$");
    private static final Pattern KEY = Pattern.compile("^\\s*\\*\\*(DEC-\\d{3})\\*\\*\\s*$");
    private static final Pattern QUESTION = Pattern.compile("^\\s*[-*]\\s*问题[：:]\\s*(.+?)\\s*$");
    private static final Pattern OPTION = Pattern.compile(
            "^\\s*[-*]\\s*`?(OPT-[A-Z0-9]+)`?[：:]\\s*(.+?)\\s*$");
    private static final Pattern RECOMMENDATION = Pattern.compile(
            "^\\s*[-*]\\s*建议[：:]\\s*`?(OPT-[A-Z0-9]+)`?\\s*$");
    private static final Pattern IMPACT = Pattern.compile("^\\s*[-*]\\s*未确认影响[：:]\\s*(.+?)\\s*$");

    public List<ParsedDecision> parse(String markdown) {
        if (markdown == null || markdown.isBlank()) return List.of();
        List<String> section = decisionSection(markdown);
        if (section.isEmpty()) return List.of();
        String visible = String.join("\n", section).trim();
        if (visible.replaceAll("[。.]$", "").equals("无待确认决策")) return List.of();

        List<ParsedDecision> decisions = new ArrayList<>();
        Builder current = null;
        for (String line : section) {
            var key = KEY.matcher(line);
            if (key.matches()) {
                if (current != null) decisions.add(current.build());
                current = new Builder(key.group(1));
                continue;
            }
            if (line.isBlank()) continue;
            if (current == null) throw invalid("待确认决策必须以 **DEC-001** 格式开始");
            var question = QUESTION.matcher(line);
            var option = OPTION.matcher(line);
            var recommendation = RECOMMENDATION.matcher(line);
            var impact = IMPACT.matcher(line);
            if (question.matches()) current.question = question.group(1).trim();
            else if (option.matches()) current.addOption(option.group(1), option.group(2).trim());
            else if (recommendation.matches()) current.recommendedOption = recommendation.group(1);
            else if (impact.matches()) current.unresolvedImpact = impact.group(1).trim();
            else if (!line.trim().matches("^[-*]\\s*选项[：:]\\s*$")) {
                throw invalid("无法识别待确认决策字段：" + line.trim());
            }
        }
        if (current != null) decisions.add(current.build());
        if (decisions.isEmpty()) throw invalid("待确认决策章节必须使用标准决策模板或写明“无待确认决策”");
        if (decisions.size() > 5) throw invalid("待确认决策不能超过 5 条");
        long distinct = decisions.stream().map(ParsedDecision::decisionKey).distinct().count();
        if (distinct != decisions.size()) throw invalid("待确认决策编号不能重复");
        return List.copyOf(decisions);
    }

    private List<String> decisionSection(String markdown) {
        String[] lines = markdown.split("\\R", -1);
        List<String> result = new ArrayList<>();
        boolean found = false;
        for (String line : lines) {
            if (!found) {
                if (SECTION.matcher(line).matches()) found = true;
                continue;
            }
            if (HEADING.matcher(line).matches()) break;
            result.add(line);
        }
        while (!result.isEmpty() && result.get(0).isBlank()) result.remove(0);
        while (!result.isEmpty() && result.get(result.size() - 1).isBlank()) result.remove(result.size() - 1);
        return result;
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    public record Option(String key, String label) {}
    public record ParsedDecision(String decisionKey, String question, List<Option> options,
                                 String recommendedOption, String unresolvedImpact) {}

    private final class Builder {
        private final String decisionKey;
        private final Map<String, String> options = new LinkedHashMap<>();
        private String question;
        private String recommendedOption;
        private String unresolvedImpact;

        private Builder(String decisionKey) { this.decisionKey = decisionKey.toUpperCase(Locale.ROOT); }

        private void addOption(String key, String label) {
            if (options.putIfAbsent(key, label) != null) throw invalid(decisionKey + " 的选项编号不能重复");
        }

        private ParsedDecision build() {
            if (question == null || question.isBlank()) throw invalid(decisionKey + " 缺少问题");
            if (options.size() < 2) throw invalid(decisionKey + " 至少需要 2 个可选项");
            if (recommendedOption == null || !options.containsKey(recommendedOption)) {
                throw invalid(decisionKey + " 的建议必须引用已有选项编号");
            }
            if (unresolvedImpact == null || unresolvedImpact.isBlank()) {
                throw invalid(decisionKey + " 缺少未确认影响");
            }
            List<Option> parsedOptions = options.entrySet().stream()
                    .map(entry -> new Option(entry.getKey(), entry.getValue())).toList();
            return new ParsedDecision(decisionKey, question, parsedOptions,
                    recommendedOption, unresolvedImpact);
        }
    }
}
