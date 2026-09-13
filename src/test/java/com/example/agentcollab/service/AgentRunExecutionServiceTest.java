package com.example.agentcollab.service;

import com.example.agentcollab.repository.AgentRunRepository;
import com.example.agentcollab.repository.OutboxJobRepository;
import com.example.agentcollab.repository.WorkflowRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentRunExecutionServiceTest {

    @Test
    void usesExponentialBackoffWithBoundedJitter() {
        var service = new AgentRunExecutionService(
                mock(OutboxJobRepository.class), mock(AgentRunRepository.class),
                mock(DocumentService.class), mock(WorkflowRepository.class),
                mock(CodeContextOrchestrator.class), 3, 1000, 8000, 0.25, 300000);

        assertThat(service.retryDelayMillis(1)).isBetween(750L, 1250L);
        assertThat(service.retryDelayMillis(2)).isBetween(1500L, 2500L);
        assertThat(service.retryDelayMillis(3)).isBetween(3000L, 5000L);
        assertThat(service.retryDelayMillis(4)).isBetween(6000L, 8000L);
    }
}
