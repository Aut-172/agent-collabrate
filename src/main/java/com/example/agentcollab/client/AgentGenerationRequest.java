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
        List<MemberContext> assignableMembers) {

    public record MemberContext(
            Long userId,
            ProjectMember.Role projectRole,
            int profileVersion,
            JsonNode profile,
            int openEffortPoints,
            Integer weeklyCapacityPoints,
            String availability) {}
}
