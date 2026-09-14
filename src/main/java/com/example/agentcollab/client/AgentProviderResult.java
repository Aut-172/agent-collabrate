package com.example.agentcollab.client;

import com.example.agentcollab.domain.DocumentFormat;
import com.fasterxml.jackson.databind.JsonNode;

public record AgentProviderResult(String content, DocumentFormat format, String summary, JsonNode rawResponse) {
    public AgentProviderResult(String content, DocumentFormat format, String summary) {
        this(content, format, summary, null);
    }
}
