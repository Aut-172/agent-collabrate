package com.example.agentcollab.dto;

import com.example.agentcollab.domain.DocumentDecision;
import com.example.agentcollab.domain.DocumentDecisionStatus;
import com.example.agentcollab.domain.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class DocumentDecisionDtos {
    private DocumentDecisionDtos() {}

    public record ResolveRequest(
            @NotBlank @Pattern(regexp = "OPT-[A-Z0-9]+") String selectedOption) {}

    public record OptionResponse(String key, String label) {}

    public record DecisionResponse(
            Long id,
            Long workflowId,
            Long documentVersionId,
            DocumentType documentType,
            String decisionKey,
            String question,
            List<OptionResponse> options,
            String recommendedOption,
            String unresolvedImpact,
            DocumentDecisionStatus status,
            String selectedOption,
            Long resolvedBy,
            Instant resolvedAt,
            Instant createdAt) {
        public static DecisionResponse from(DocumentDecision decision) {
            List<OptionResponse> options = new ArrayList<>();
            decision.getOptionsJson().forEach(option -> options.add(new OptionResponse(
                    option.path("key").asText(), option.path("label").asText())));
            return new DecisionResponse(decision.getId(), decision.getWorkflowId(),
                    decision.getDocumentVersionId(), decision.getDocumentType(), decision.getDecisionKey(),
                    decision.getQuestion(), List.copyOf(options), decision.getRecommendedOption(),
                    decision.getUnresolvedImpact(), decision.getStatus(), decision.getSelectedOption(),
                    decision.getResolvedBy(), decision.getResolvedAt(), decision.getCreatedAt());
        }
    }
}
