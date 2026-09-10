package com.example.agentcollab.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "code_context_files")
public class CodeContextFile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "context_version_id", nullable = false, updatable = false) private Long contextVersionId;
    @Column(nullable = false, columnDefinition = "text", updatable = false) private String path;
    @Column(name = "content_hash", length = 128, updatable = false) private String contentHash;
    @Enumerated(EnumType.STRING) @Column(name = "evidence_type", nullable = false, length = 30, updatable = false)
    private CodeEvidenceType evidenceType;
    @Column(nullable = false, columnDefinition = "text", updatable = false) private String summary;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "important_symbols", nullable = false, columnDefinition = "jsonb", updatable = false)
    private JsonNode importantSymbols;
    @Column(columnDefinition = "text", updatable = false) private String excerpt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected CodeContextFile() {}
    public CodeContextFile(Long contextVersionId, String path, String contentHash, CodeEvidenceType evidenceType,
                           String summary, JsonNode importantSymbols, String excerpt) {
        this.contextVersionId = contextVersionId; this.path = path; this.contentHash = contentHash;
        this.evidenceType = evidenceType; this.summary = summary; this.importantSymbols = importantSymbols.deepCopy();
        this.excerpt = excerpt; this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public Long getContextVersionId() { return contextVersionId; }
    public String getPath() { return path; }
    public String getContentHash() { return contentHash; }
    public CodeEvidenceType getEvidenceType() { return evidenceType; }
    public String getSummary() { return summary; }
    public JsonNode getImportantSymbols() { return importantSymbols.deepCopy(); }
    public String getExcerpt() { return excerpt; }
}
