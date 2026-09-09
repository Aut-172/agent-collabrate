package com.example.agentcollab.client;

public interface AgentProviderClient {
    String providerName();
    String modelName();
    AgentProviderResult generate(AgentGenerationRequest request);
}
