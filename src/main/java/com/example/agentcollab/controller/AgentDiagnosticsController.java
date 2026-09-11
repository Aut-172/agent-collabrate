package com.example.agentcollab.controller;

import com.example.agentcollab.client.AgentGenerationRequest;
import com.example.agentcollab.client.AgentProviderClient;
import com.example.agentcollab.client.AgentProviderResult;
import com.example.agentcollab.domain.DocumentFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent-diagnostics")
@Profile("agent-diagnostics")
@ConditionalOnProperty(name = "app.agent.diagnostics-enabled", havingValue = "true")
public class AgentDiagnosticsController {
    private final AgentProviderClient provider;

    public AgentDiagnosticsController(AgentProviderClient provider) {
        this.provider = provider;
    }

    /**
     * Runs one provider call without creating an AgentRun or changing a Workflow.
     * The request is echoed so a test sample can be reviewed alongside the raw output.
     */
    @PostMapping("/generate")
    public DiagnosticResponse generate(@RequestBody AgentGenerationRequest request) {
        AgentProviderResult result = provider.generate(request);
        return new DiagnosticResponse(request, provider.providerName(), provider.modelName(),
                result.content(), result.format(), result.summary());
    }

    public record DiagnosticResponse(
            AgentGenerationRequest request,
            String provider,
            String model,
            String output,
            DocumentFormat format,
            String summary) {}
}
