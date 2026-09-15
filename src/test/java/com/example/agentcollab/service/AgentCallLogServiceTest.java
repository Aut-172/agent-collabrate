package com.example.agentcollab.service;

import com.example.agentcollab.domain.AgentCallRecord;
import com.example.agentcollab.domain.AgentRun;
import com.example.agentcollab.domain.AgentRunType;
import com.example.agentcollab.domain.Project;
import com.example.agentcollab.domain.ProjectMember;
import com.example.agentcollab.domain.Workflow;
import com.example.agentcollab.domain.WorkflowCompletionMode;
import com.example.agentcollab.dto.AgentCallLogDtos;
import com.example.agentcollab.repository.AgentCallRecordRepository;
import com.example.agentcollab.repository.AgentRunRepository;
import com.example.agentcollab.repository.ProjectRepository;
import com.example.agentcollab.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentCallLogServiceTest {
    @Mock ProjectRepository projects;
    @Mock ProjectAccessService access;
    @Mock WorkflowRepository workflows;
    @Mock AgentRunRepository agentRuns;
    @Mock AgentCallRecordRepository records;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void returnsLeaderOnlyCallDetailsAndTokenSummary() throws Exception {
        Project project = new Project("project", "https://example.test/repo", "github", "main", 1L);
        ReflectionTestUtils.setField(project, "id", 7L);
        Workflow workflow = new Workflow(7L, "workflow", "description", com.example.agentcollab.domain.IntentLevel.FEATURE,
                WorkflowCompletionMode.CI_REQUIRED, null, 1L);
        ReflectionTestUtils.setField(workflow, "id", 21L);
        AgentRun run = new AgentRun(21L, AgentRunType.GENERATE_DESIGN, "openai", "gpt-test", "summary");
        ReflectionTestUtils.setField(run, "id", 41L);
        AgentCallRecord call = new AgentCallRecord(41L, 1, "openai", "gpt-test", "GENERATE_DESIGN",
                json.readTree("{\"input\":\"safe\"}"));
        ReflectionTestUtils.setField(call, "id", 51L);
        call.succeed(json.readTree("{\"usage\":{\"input_tokens\":10,\"output_tokens\":6,\"total_tokens\":20,\"output_tokens_details\":{\"reasoning_tokens\":4}}}"), 120);

        when(access.requireLeader(7L, 1L)).thenReturn(mock(ProjectMember.class));
        when(projects.findById(7L)).thenReturn(Optional.of(project));
        when(workflows.findByProjectIdOrderByCreatedAtAsc(7L)).thenReturn(List.of(workflow));
        when(agentRuns.findByWorkflowIdInOrderByCreatedAtDesc(List.of(21L))).thenReturn(List.of(run));
        when(records.findByAgentRunIdInOrderByCreatedAtDesc(List.of(41L))).thenReturn(List.of(call));

        AgentCallLogDtos.Response result = new AgentCallLogService(projects, access, workflows, agentRuns, records)
                .list(1L, 7L);

        assertThat(result.summary().totalCalls()).isEqualTo(1);
        assertThat(result.summary().succeededCalls()).isEqualTo(1);
        assertThat(result.summary().totalTokens()).isEqualTo(20);
        assertThat(result.summary().totalDurationMs()).isEqualTo(120);
        assertThat(result.summary().byRunType()).containsEntry("GENERATE_DESIGN", 1L);
        assertThat(result.records()).singleElement().satisfies(value -> {
            assertThat(value.workflowId()).isEqualTo(21L);
            assertThat(value.request().path("input").asText()).isEqualTo("safe");
            assertThat(value.totalTokens()).isEqualTo(20);
        });
        verify(access).requireLeader(7L, 1L);
    }
}
