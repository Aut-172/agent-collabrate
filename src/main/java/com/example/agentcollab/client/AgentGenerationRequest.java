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
            String availability,
            double workloadRatio) {

        /**
         * Keeps callers that construct request fixtures compatible while exposing the
         * derived load signal to the planning agent.
         */
        public MemberContext(Long userId, ProjectMember.Role projectRole, int profileVersion,
                             JsonNode profile, int openEffortPoints, Integer weeklyCapacityPoints,
                             String availability) {
            this(userId, projectRole, profileVersion, profile, openEffortPoints,
                    weeklyCapacityPoints, availability, calculateWorkloadRatio(openEffortPoints,
                            weeklyCapacityPoints));
        }

        private static double calculateWorkloadRatio(int openEffortPoints, Integer weeklyCapacityPoints) {
            if (weeklyCapacityPoints == null || weeklyCapacityPoints <= 0) return 0.0d;
            return (double) Math.max(0, openEffortPoints) / weeklyCapacityPoints;
        }
    }

    public record DecisionContext(
            Long documentVersionId,
            String documentType,
            String decisionKey,
            String question,
            String selectedOption,
            String selectedOptionLabel) {}
}
