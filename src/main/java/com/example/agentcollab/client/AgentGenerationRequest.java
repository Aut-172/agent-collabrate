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
        RepoInventoryInput repoInventory,
        CodeContextInput codeContext,
        List<DecisionContext> resolvedDecisions) {

    public AgentGenerationRequest(Long workflowId, AgentRunType runType, IntentLevel intentLevel,
                                  String title, String description, String design, String spec,
                                  List<MemberContext> assignableMembers, RepoInventoryInput repoInventory,
                                  CodeContextInput codeContext) {
        this(workflowId, runType, intentLevel, title, description, design, spec, assignableMembers,
                repoInventory, codeContext, List.of());
    }

    public record RepoInventoryInput(Long inventoryVersionId, String commitSha, JsonNode repositoryProfile,
                                     JsonNode treeSummary, List<InventoryFileContext> inventoryFiles) {}

    public record InventoryFileContext(String path, String fileType, long sizeBytes,
                                       String contentHash, String indexedSummary) {}

    public record CodeContextInput(Long codeContextVersionId, Long contextPlanId, Long inventoryVersionId,
                                   String baseCommitSha, JsonNode repositoryProfile, JsonNode evidence,
                                   List<EvidenceFileContext> files) {}

    public record EvidenceFileContext(String path, String evidenceType, String contentHash,
                                      String summary, JsonNode importantSymbols, String excerpt) {}

    public record MemberContext(
            Long userId,
            ProjectMember.Role projectRole,
            int profileVersion,
            JsonNode profile,
            int openEffortPoints,
            Integer weeklyCapacityPoints,
            String availability) {}

    public record DecisionContext(
            Long documentVersionId,
            String documentType,
            String decisionKey,
            String question,
            String selectedOption,
            String selectedOptionLabel) {}
}
