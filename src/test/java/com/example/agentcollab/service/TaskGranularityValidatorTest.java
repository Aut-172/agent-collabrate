package com.example.agentcollab.service;

import com.example.agentcollab.domain.IntentLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskGranularityValidatorTest {
    private final ObjectMapper json = new ObjectMapper();
    private final TaskGranularityValidator validator = new TaskGranularityValidator();

    @Test
    void warnsWhenChangeIsSplitIntoMultipleTasksForOneMember() throws Exception {
        var root = json.readTree("""
                {"tasks":[
                  {"taskKey":"A","title":"前端","acceptanceCriteria":["done"],"dependencies":[]},
                  {"taskKey":"B","title":"后端","acceptanceCriteria":["done"],"dependencies":["A"]}],
                 "assignments":[{"taskKey":"A","userId":1},{"taskKey":"B","userId":1}]}
                """);
        assertThat(validator.warnings(root, IntentLevel.CHANGE)).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void doesNotWarnArchitecturePlans() throws Exception {
        assertThat(validator.warnings(json.readTree("{\"tasks\":[]}"), IntentLevel.ARCHITECTURE)).isEmpty();
    }
}
