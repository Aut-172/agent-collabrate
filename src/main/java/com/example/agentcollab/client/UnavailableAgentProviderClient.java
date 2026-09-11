package com.example.agentcollab.client;

import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Keeps production startup explicit until a real Agent Provider adapter is configured. */
@Component
@Profile("!test & !mock-provider")
@ConditionalOnProperty(name = "app.agent.provider", havingValue = "unconfigured", matchIfMissing = true)
public class UnavailableAgentProviderClient implements AgentProviderClient {
    @Override public String providerName() { return "unconfigured"; }
    @Override public String modelName() { return "unconfigured"; }
    @Override public AgentProviderResult generate(AgentGenerationRequest request) {
        throw new AgentProviderException("AGENT_PROVIDER_NOT_CONFIGURED",
                "Agent Provider is not configured", false);
    }
}
