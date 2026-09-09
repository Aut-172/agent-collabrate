package com.example.agentcollab.client;

import com.example.agentcollab.domain.DocumentFormat;

public record AgentProviderResult(String content, DocumentFormat format, String summary) {}
