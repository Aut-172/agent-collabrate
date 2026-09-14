package com.example.agentcollab.service;

import com.example.agentcollab.domain.IntentLevel;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Non-blocking quality checks for plans that are technically valid but too finely split. */
@Component
public class TaskGranularityValidator {
    private static final Set<String> LAYER_TITLES = Set.of(
            "frontend", "backend", "后端", "前端", "test", "tests", "测试", "database", "数据库", "sql");

    public record Warning(String code, String message, List<String> taskKeys, String suggestedAction) {}

    public List<Warning> analyze(JsonNode root, IntentLevel level) {
        if (level == IntentLevel.ARCHITECTURE) return List.of();
        JsonNode tasks = root.path("tasks");
        List<Warning> warnings = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        tasks.forEach(task -> keys.add(task.path("taskKey").asText()));
        int count = tasks.size();
        if (level == IntentLevel.CHANGE && count > 1) {
            warnings.add(new Warning("TASK_GRANULARITY_WARNING", "Change 默认应保持为一个端到端 Task", keys, "MERGE"));
        }
        if (level == IntentLevel.FEATURE && count > 3) {
            warnings.add(new Warning("TASK_GRANULARITY_WARNING", "Feature 超过 3 个 Task，需要明确独立交付边界", keys, "MERGE_OR_KEEP_SPLIT"));
        }
        Set<String> titles = new HashSet<>();
        boolean allLayerNames = true;
        for (JsonNode task : tasks) {
            String title = task.path("title").asText("").trim().toLowerCase(Locale.ROOT);
            titles.add(title);
            if (!LAYER_TITLES.contains(title)) allLayerNames = false;
        }
        if (count > 1 && allLayerNames) {
            warnings.add(new Warning("TASK_GRANULARITY_WARNING", "Task 不能只按前端/后端/测试/数据库层级机械拆分", keys, "MERGE"));
        }
        if (count > 1) {
            Set<String> criteria = new HashSet<>();
            for (JsonNode task : tasks) {
                String normalized = task.path("acceptanceCriteria").toString().toLowerCase(Locale.ROOT);
                criteria.add(normalized);
            }
            if (criteria.size() < count) {
                warnings.add(new Warning("TASK_GRANULARITY_WARNING", "多个 Task 的验收标准重复，可能应合并为一个交付闭环", keys, "MERGE"));
            }
            Set<Long> assignees = new HashSet<>();
            root.path("assignments").forEach(a -> assignees.add(a.path("userId").asLong()));
            if (assignees.size() == 1) {
                warnings.add(new Warning("TASK_GRANULARITY_WARNING", "多个 Task 由同一成员负责，需确认是否存在独立交付边界", keys, "KEEP_SPLIT_WITH_REASON"));
            }
            long withDependencies = 0;
            for (JsonNode task : tasks) if (task.path("dependencies").size() > 0) withDependencies++;
            if (withDependencies == count - 1 && count > 2) {
                warnings.add(new Warning("TASK_GRANULARITY_WARNING", "Task 全部串行依赖且无并行价值，建议合并或改为垂直切片", keys, "MERGE"));
            }
        }
        return warnings.stream().collect(Collectors.collectingAndThen(
                Collectors.toMap(Warning::message, w -> w, (a, b) -> a, java.util.LinkedHashMap::new),
                map -> List.copyOf(map.values())));
    }

    public List<String> warnings(JsonNode root, IntentLevel level) {
        return analyze(root, level).stream()
                .map(w -> w.code() + ": " + w.message()).toList();
    }
}
