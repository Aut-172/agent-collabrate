package com.example.agentcollab.client;

import com.example.agentcollab.domain.AgentRunType;
import com.example.agentcollab.domain.IntentLevel;
import com.example.agentcollab.domain.ProjectMember;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record AgentGenerationRequest(
        Long workflowId,
        AgentRunType runType,
        IntentLevel intentLevel,
        String title,
        String description,
        String design,
        String spec,
        List<MemberContext> assignableMembers,
        CodeContextInput codeContext) {

    public record CodeContextInput(Long inventoryVersionId, String commitSha, JsonNode repositoryProfile,
                                   JsonNode treeSummary, List<InventoryFileContext> inventoryFiles) {}

    public record InventoryFileContext(String path, String fileType, long sizeBytes,
                                       String contentHash, String indexedSummary) {}

    public record MemberContext(
            Long userId,
            ProjectMember.Role projectRole,
            int profileVersion,
            JsonNode profile,
            int openEffortPoints,
            Integer weeklyCapacityPoints,
            String availability) {}
}
