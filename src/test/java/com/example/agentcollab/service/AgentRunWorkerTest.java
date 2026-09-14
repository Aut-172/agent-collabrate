package com.example.agentcollab.service;

import com.example.agentcollab.client.AgentProviderClient;
import com.example.agentcollab.client.AgentGenerationRequest;
import com.example.agentcollab.client.AgentProviderResult;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.domain.AgentRunType;
import com.example.agentcollab.domain.DocumentFormat;
import com.example.agentcollab.domain.IntentLevel;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;

class AgentRunWorkerTest {

    @Test
    void pollSubmitsConfiguredNumberOfWorkersAndRecoversTimeouts() {
        AgentRunExecutionService executions = mock(AgentRunExecutionService.class);
        AgentRequestFactory requests = mock(AgentRequestFactory.class);
        AgentOutputValidator outputValidator = mock(AgentOutputValidator.class);
        AgentProviderClient provider = mock(AgentProviderClient.class);
        List<Runnable> submitted = new ArrayList<>();
        Executor executor = submitted::add;
        AgentRunWorker worker = new AgentRunWorker(
                executions, requests, outputValidator, provider, executor, 3);

        worker.poll();

        verify(executions).recoverTimedOut();
        assertThat(submitted).hasSize(3);
    }

    @Test
    void preservesApiExceptionCodeAndMessageInsteadOfHidingItAsUnexpectedError() {
        AgentRunExecutionService executions = mock(AgentRunExecutionService.class);
        AgentRequestFactory requests = mock(AgentRequestFactory.class);
        AgentOutputValidator outputValidator = mock(AgentOutputValidator.class);
        AgentProviderClient provider = mock(AgentProviderClient.class);
        var claimed = new AgentRunExecutionService.ClaimedAgentJob(7L, 9L);
        when(executions.claimNext()).thenReturn(java.util.Optional.of(claimed));
        when(requests.create(9L)).thenThrow(new ApiException(HttpStatus.CONFLICT,
                "CODE_CONTEXT_STALE", "请刷新 Code Context"));
        AgentRunWorker worker = new AgentRunWorker(
                executions, requests, outputValidator, provider, Runnable::run, 1);

        worker.processNext();

        verify(executions).handleFailure(claimed, "CODE_CONTEXT_STALE", "请刷新 Code Context", false);
    }

    @Test
    void preservesDatabaseConstraintCauseWhenContextPersistenceFails() {
        AgentRunExecutionService executions = mock(AgentRunExecutionService.class);
        AgentRequestFactory requests = mock(AgentRequestFactory.class);
        AgentOutputValidator outputValidator = mock(AgentOutputValidator.class);
        AgentProviderClient provider = mock(AgentProviderClient.class);
        var claimed = new AgentRunExecutionService.ClaimedAgentJob(8L, 10L);
        when(executions.claimNext()).thenReturn(java.util.Optional.of(claimed));
        when(requests.create(10L)).thenThrow(new DataIntegrityViolationException(
                "could not execute statement", new RuntimeException(
                        "duplicate key value violates unique constraint code_context_runs_active_evidence_plan_unique")));
        AgentRunWorker worker = new AgentRunWorker(
                executions, requests, outputValidator, provider, Runnable::run, 1);

        worker.processNext();

        org.mockito.ArgumentCaptor<String> message = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(executions).handleFailure(org.mockito.Mockito.eq(claimed),
                org.mockito.Mockito.eq("AGENT_EXECUTION_FAILED"), message.capture(), org.mockito.Mockito.eq(false));
        assertThat(message.getValue()).contains("duplicate key value violates unique constraint")
                .contains("RuntimeException");
    }

    @Test
    void persistsProviderRequestAndFeedbackForEachCall() {
        AgentRunExecutionService executions = mock(AgentRunExecutionService.class);
        AgentRequestFactory requests = mock(AgentRequestFactory.class);
        AgentOutputValidator outputValidator = mock(AgentOutputValidator.class);
        AgentProviderClient provider = mock(AgentProviderClient.class);
        AgentCallRecordService callRecords = mock(AgentCallRecordService.class);
        var claimed = new AgentRunExecutionService.ClaimedAgentJob(11L, 12L);
        var request = new AgentGenerationRequest(1L, AgentRunType.GENERATE_DESIGN, IntentLevel.FEATURE,
                "title", "description", null, null, List.of(), null, null);
        var result = new AgentProviderResult("# Design", DocumentFormat.MARKDOWN, "ok");
        var callRecord = mock(com.example.agentcollab.domain.AgentCallRecord.class);
        when(executions.claimNext()).thenReturn(java.util.Optional.of(claimed));
        when(requests.create(12L)).thenReturn(request);
        when(provider.generate(request)).thenReturn(result);
        when(callRecords.start(12L, "mock", "test-model", request, null)).thenReturn(callRecord);
        when(callRecord.getId()).thenReturn(91L);
        when(provider.providerName()).thenReturn("mock");
        when(provider.modelName()).thenReturn("test-model");
        AgentRunWorker worker = new AgentRunWorker(
                executions, requests, outputValidator, provider, callRecords, Runnable::run, 1);

        worker.processNext();

        verify(callRecords).start(12L, "mock", "test-model", request, null);
        verify(callRecords).succeed(org.mockito.Mockito.eq(91L), org.mockito.Mockito.same(result), anyLong());
        verify(executions).complete(claimed, result);
    }
}
