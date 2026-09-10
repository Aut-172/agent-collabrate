package com.example.agentcollab.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "document_versions", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workflow_id", "document_type", "version_no"}))
public class DocumentVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "workflow_id", nullable = false, updatable = false)
    private Long workflowId;
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30, updatable = false)
    private DocumentType documentType;
    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;
    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String content;
    @Enumerated(EnumType.STRING)
    @Column(name = "content_format", nullable = false, length = 20, updatable = false)
    private DocumentFormat contentFormat;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private DocumentSource source;
    @Column(name = "created_by", updatable = false)
    private Long createdBy;
    @Column(name = "agent_run_id", updatable = false)
    private Long agentRunId;
    @Column(name = "code_context_version_id", updatable = false)
    private Long codeContextVersionId;
    @Column(name = "is_confirmed", nullable = false)
    private boolean confirmed;
    @Column(name = "confirmed_by")
    private Long confirmedBy;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentVersion() {}

    public static DocumentVersion byUser(Long workflowId, DocumentType type, int versionNo,
                                         String content, DocumentFormat format, Long createdBy) {
        return byUser(workflowId, type, versionNo, content, format, createdBy, null);
    }

    public static DocumentVersion byUser(Long workflowId, DocumentType type, int versionNo,
                                         String content, DocumentFormat format, Long createdBy,
                                         Long codeContextVersionId) {
        return new DocumentVersion(workflowId, type, versionNo, content, format, DocumentSource.USER, createdBy,
                null, codeContextVersionId);
    }

    public static DocumentVersion byAgent(Long workflowId, DocumentType type, int versionNo,
                                          String content, DocumentFormat format, Long agentRunId,
                                          Long codeContextVersionId) {
        return new DocumentVersion(workflowId, type, versionNo, content, format, DocumentSource.AGENT, null,
                agentRunId, codeContextVersionId);
    }

    private DocumentVersion(Long workflowId, DocumentType type, int versionNo, String content,
                            DocumentFormat format, DocumentSource source, Long createdBy, Long agentRunId) {
        this(workflowId, type, versionNo, content, format, source, createdBy, agentRunId, null);
    }

    private DocumentVersion(Long workflowId, DocumentType type, int versionNo, String content,
                            DocumentFormat format, DocumentSource source, Long createdBy, Long agentRunId,
                            Long codeContextVersionId) {
        this.workflowId = workflowId;
        this.documentType = type;
        this.versionNo = versionNo;
        this.content = content;
        this.contentFormat = format;
        this.source = source;
        this.createdBy = createdBy;
        this.agentRunId = agentRunId;
        this.codeContextVersionId = codeContextVersionId;
        this.createdAt = Instant.now();
    }

    public void confirm(Long userId) {
        if (confirmed) return;
        this.confirmed = true;
        this.confirmedBy = userId;
        this.confirmedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getWorkflowId() { return workflowId; }
    public DocumentType getDocumentType() { return documentType; }
    public int getVersionNo() { return versionNo; }
    public String getContent() { return content; }
    public DocumentFormat getContentFormat() { return contentFormat; }
    public DocumentSource getSource() { return source; }
    public Long getCreatedBy() { return createdBy; }
    public Long getAgentRunId() { return agentRunId; }
    public Long getCodeContextVersionId() { return codeContextVersionId; }
    public boolean isConfirmed() { return confirmed; }
    public Long getConfirmedBy() { return confirmedBy; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
