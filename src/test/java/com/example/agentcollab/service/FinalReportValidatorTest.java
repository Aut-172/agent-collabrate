package com.example.agentcollab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinalReportValidatorTest {
    private final ObjectMapper json = new ObjectMapper();
    private final FinalReportValidator validator = new FinalReportValidator();

    @Test
    void acceptsACompleteVersionOneReport() throws Exception {
        assertThatCode(() -> validator.validate(json.readTree(validReport())))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingFieldsAndUnknownProperties() throws Exception {
        var missingSummary = json.readTree(validReport());
        ((com.fasterxml.jackson.databind.node.ObjectNode) missingSummary).remove("summary");
        assertThatThrownBy(() -> validator.validate(missingSummary))
                .isInstanceOf(FinalReportValidationException.class);

        var unknownProperty = json.readTree(validReport());
        ((com.fasterxml.jackson.databind.node.ObjectNode) unknownProperty).put("trustedCiStatus", "PASSED");
        assertThatThrownBy(() -> validator.validate(unknownProperty))
                .isInstanceOf(FinalReportValidationException.class);
    }

    private String validReport() {
        return """
                {
                  "schemaVersion": "1.0",
                  "taskId": "TASK-001",
                  "packageId": 1,
                  "packageVersion": 1,
                  "packageHash": "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                  "outcome": "READY_FOR_REVIEW",
                  "summary": "Implemented the task",
                  "changedFiles": ["src/main/java/example/Delivery.java"],
                  "tests": [{"command": "mvn test", "status": "PASSED", "summary": "Passed locally"}],
                  "git": {
                    "branchName": "agent/wf-1/task-001",
                    "commitSha": "0123456789abcdef0123456789abcdef01234567",
                    "pullRequestUrl": null
                  },
                  "acceptanceCriteria": [{"criterion": "Delivery works", "status": "PASSED", "evidence": "Test"}],
                  "unresolvedIssues": [],
                  "outOfScopeChanges": [],
                  "blockers": []
                }
                """;
    }
}
