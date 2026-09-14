package com.example.agentcollab.client;

import com.fasterxml.jackson.databind.JsonNode;

public interface AgentProviderClient {
    String providerName();
    String modelName();
    AgentProviderResult generate(AgentGenerationRequest request);

    /** Structured request envelope for durable diagnostics; implementations must omit credentials. */
    default JsonNode requestPayload(AgentGenerationRequest request) { return null; }
}
