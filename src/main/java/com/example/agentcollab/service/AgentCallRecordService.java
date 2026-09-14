package com.example.agentcollab.service;

import com.example.agentcollab.client.AgentGenerationRequest;
import com.example.agentcollab.client.AgentProviderResult;
import com.example.agentcollab.domain.AgentCallRecord;
import com.example.agentcollab.repository.AgentCallRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentCallRecordService {
    private final AgentCallRecordRepository records;
    private final ObjectMapper json;

    public AgentCallRecordService(AgentCallRecordRepository records, ObjectMapper json) {
        this.records = records;
        this.json = json;
    }

    @Transactional
    public AgentCallRecord start(Long agentRunId, String provider, String model,
                                 AgentGenerationRequest request) {
        return start(agentRunId, provider, model, request, null);
    }

    @Transactional
    public AgentCallRecord start(Long agentRunId, String provider, String model,
                                 AgentGenerationRequest request, JsonNode requestPayload) {
        int attemptNo = (int) records.countByAgentRunId(agentRunId) + 1;
        AgentCallRecord record = new AgentCallRecord(agentRunId, attemptNo, provider, model,
                request.runType().name(), requestPayload != null ? requestPayload : json.valueToTree(request));
        return records.saveAndFlush(record);
    }

    @Transactional
    public void succeed(Long recordId, AgentProviderResult result, long durationMs) {
        AgentCallRecord record = records.findById(recordId).orElse(null);
        if (record == null || record.getStatus() != com.example.agentcollab.domain.AgentCallStatus.RUNNING) return;
        JsonNode response = result.rawResponse() != null
                ? result.rawResponse()
                : json.valueToTree(result);
        record.succeed(response, durationMs);
    }

    @Transactional
    public void fail(Long recordId, String code, String message, boolean retryable, long durationMs) {
        fail(recordId, null, code, message, retryable, durationMs);
    }

    @Transactional
    public void fail(Long recordId, AgentProviderResult result, String code, String message,
                     boolean retryable, long durationMs) {
        AgentCallRecord record = records.findById(recordId).orElse(null);
        if (record == null || record.getStatus() != com.example.agentcollab.domain.AgentCallStatus.RUNNING) return;
        JsonNode response = result == null ? null : result.rawResponse() != null
                ? result.rawResponse() : json.valueToTree(result);
        record.fail(response, safe(code, "AGENT_EXECUTION_FAILED", 100),
                safe(message, "Agent execution failed", 2000), retryable, durationMs);
    }

    private String safe(String value, String fallback, int maxLength) {
        String result = value == null || value.isBlank() ? fallback : value;
        result = result.replace('\r', ' ').replace('\n', ' ');
        return result.length() <= maxLength ? result : result.substring(0, maxLength);
    }
}
