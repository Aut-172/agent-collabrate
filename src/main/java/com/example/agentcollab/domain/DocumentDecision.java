package com.example.agentcollab.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "document_decisions", uniqueConstraints = @UniqueConstraint(columnNames = {
        "document_version_id", "decision_key"}))
public class DocumentDecision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "workflow_id", nullable = false, updatable = false) private Long workflowId;
    @Column(name = "document_version_id", nullable = false, updatable = false) private Long documentVersionId;
    @Enumerated(EnumType.STRING) @Column(name = "document_type", nullable = false, length = 30, updatable = false)
    private DocumentType documentType;
    @Column(name = "decision_key", nullable = false, length = 20, updatable = false) private String decisionKey;
    @Column(nullable = false, columnDefinition = "text", updatable = false) private String question;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "options_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private JsonNode optionsJson;
    @Column(name = "recommended_option", nullable = false, length = 30, updatable = false)
    private String recommendedOption;
    @Column(name = "unresolved_impact", nullable = false, columnDefinition = "text", updatable = false)
    private String unresolvedImpact;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DocumentDecisionStatus status;
    @Column(name = "selected_option", length = 30) private String selectedOption;
    @Column(name = "resolved_by") private Long resolvedBy;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected DocumentDecision() {}

    public DocumentDecision(Long workflowId, Long documentVersionId, DocumentType documentType,
                            String decisionKey, String question, JsonNode optionsJson,
                            String recommendedOption, String unresolvedImpact) {
        this.workflowId = workflowId;
        this.documentVersionId = documentVersionId;
        this.documentType = documentType;
        this.decisionKey = decisionKey;
        this.question = question;
        this.optionsJson = optionsJson.deepCopy();
        this.recommendedOption = recommendedOption;
        this.unresolvedImpact = unresolvedImpact;
        this.status = DocumentDecisionStatus.OPEN;
        this.createdAt = Instant.now();
    }

    public void resolve(String option, Long actorId) {
        if (status != DocumentDecisionStatus.OPEN) throw new IllegalStateException("Decision is not open");
        selectedOption = option;
        resolvedBy = actorId;
        resolvedAt = Instant.now();
        status = DocumentDecisionStatus.RESOLVED;
    }

    public Long getId() { return id; }
    public Long getWorkflowId() { return workflowId; }
    public Long getDocumentVersionId() { return documentVersionId; }
    public DocumentType getDocumentType() { return documentType; }
    public String getDecisionKey() { return decisionKey; }
    public String getQuestion() { return question; }
    public JsonNode getOptionsJson() { return optionsJson.deepCopy(); }
    public String getRecommendedOption() { return recommendedOption; }
    public String getUnresolvedImpact() { return unresolvedImpact; }
    public DocumentDecisionStatus getStatus() { return status; }
    public String getSelectedOption() { return selectedOption; }
    public Long getResolvedBy() { return resolvedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
