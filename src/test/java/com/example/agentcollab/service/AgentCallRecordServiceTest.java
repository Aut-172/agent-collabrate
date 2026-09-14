package com.example.agentcollab.service;

import com.example.agentcollab.client.AgentGenerationRequest;
import com.example.agentcollab.client.AgentProviderResult;
import com.example.agentcollab.domain.AgentCallRecord;
import com.example.agentcollab.domain.AgentRunType;
import com.example.agentcollab.domain.DocumentFormat;
import com.example.agentcollab.domain.IntentLevel;
import com.example.agentcollab.repository.AgentCallRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentCallRecordServiceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void persistsStructuredRequestAndIncrementsAttemptNumber() {
        AgentCallRecordRepository records = mock(AgentCallRecordRepository.class);
        when(records.countByAgentRunId(7L)).thenReturn(1L);
        when(records.saveAndFlush(any(AgentCallRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AgentCallRecordService service = new AgentCallRecordService(records, json);

        AgentCallRecord record = service.start(7L, "mock", "test-model", request());

        assertThat(record.getAgentRunId()).isEqualTo(7L);
        assertThat(record.getAttemptNo()).isEqualTo(2);
        assertThat(record.getStatus().name()).isEqualTo("RUNNING");
        assertThat(record.getRequestJson().path("runType").asText()).isEqualTo("GENERATE_DESIGN");
        verify(records).saveAndFlush(record);
    }

    @Test
    void recordsSuccessfulProviderFeedbackAndDuration() {
        AgentCallRecordRepository records = mock(AgentCallRecordRepository.class);
        AgentCallRecord record = new AgentCallRecord(7L, 1, "mock", "test-model",
                "GENERATE_DESIGN", json.createObjectNode().put("title", "test"));
        when(records.findById(9L)).thenReturn(Optional.of(record));
        AgentCallRecordService service = new AgentCallRecordService(records, json);

        service.succeed(9L, new AgentProviderResult("# Design", DocumentFormat.MARKDOWN, "ok"), 125);

        assertThat(record.getStatus().name()).isEqualTo("SUCCEEDED");
        assertThat(record.getResponseJson().path("content").asText()).isEqualTo("# Design");
        assertThat(record.getDurationMs()).isEqualTo(125L);
        assertThat(record.getFinishedAt()).isNotNull();
    }

    @Test
    void recordsProviderFailureWithoutLeakingNewlines() {
        AgentCallRecordRepository records = mock(AgentCallRecordRepository.class);
        AgentCallRecord record = new AgentCallRecord(7L, 1, "mock", "test-model",
                "GENERATE_DESIGN", json.createObjectNode());
        when(records.findById(9L)).thenReturn(Optional.of(record));
        AgentCallRecordService service = new AgentCallRecordService(records, json);

        service.fail(9L, "OPENAI_HTTP_500", "provider failed\nwith details", true, 20);

        assertThat(record.getStatus().name()).isEqualTo("FAILED");
        assertThat(record.getErrorCode()).isEqualTo("OPENAI_HTTP_500");
        assertThat(record.getErrorMessage()).isEqualTo("provider failed with details");
        assertThat(record.getRetryable()).isTrue();
        assertThat(record.getDurationMs()).isEqualTo(20L);
    }

    private AgentGenerationRequest request() {
        return new AgentGenerationRequest(1L, AgentRunType.GENERATE_DESIGN, IntentLevel.FEATURE,
                "title", "description", null, null, java.util.List.of(), null, null);
    }
}
