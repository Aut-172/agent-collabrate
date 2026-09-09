package com.example.agentcollab.service;

import com.example.agentcollab.client.AgentProviderClient;
import com.example.agentcollab.client.AgentProviderException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AgentRunWorker {
    private final AgentRunExecutionService executions;
    private final AgentRequestFactory requests;
    private final AgentOutputValidator outputValidator;
    private final AgentProviderClient provider;

    public AgentRunWorker(AgentRunExecutionService executions, AgentRequestFactory requests,
                          AgentOutputValidator outputValidator, AgentProviderClient provider) {
        this.executions = executions;
        this.requests = requests;
        this.outputValidator = outputValidator;
        this.provider = provider;
    }

    @Scheduled(fixedDelayString = "${app.agent.worker.poll-delay-ms:1000}")
    public void poll() {
        executions.recoverTimedOut();
        processNext();
    }

    public boolean processNext() {
        var claimed = executions.claimNext();
        if (claimed.isEmpty()) return false;
        try {
            var request = requests.create(claimed.get().runId());
            var result = provider.generate(request);
            outputValidator.validate(request, result);
            executions.complete(claimed.get(), result);
        } catch (AgentProviderException ex) {
            executions.handleFailure(claimed.get(), ex.getCode(), ex.getMessage(), ex.isRetryable());
        } catch (RuntimeException ex) {
            executions.handleFailure(claimed.get(), "AGENT_EXECUTION_FAILED",
                    "Unexpected provider error: " + ex.getClass().getSimpleName(), false);
        }
        return true;
    }
}
