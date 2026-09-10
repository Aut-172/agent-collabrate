package com.example.agentcollab.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "repo_inventory_files", uniqueConstraints = @UniqueConstraint(columnNames = {
        "inventory_version_id", "path"}))
public class RepoInventoryFile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "inventory_version_id", nullable = false, updatable = false) private Long inventoryVersionId;
    @Column(nullable = false, columnDefinition = "text", updatable = false) private String path;
    @Enumerated(EnumType.STRING) @Column(name = "file_type", nullable = false, length = 30, updatable = false)
    private RepoFileType fileType;
    @Column(name = "size_bytes", nullable = false, updatable = false) private long sizeBytes;
    @Column(name = "content_hash", length = 128, updatable = false) private String contentHash;
    @Column(name = "indexed_summary", columnDefinition = "text", updatable = false) private String indexedSummary;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected RepoInventoryFile() {}

    public RepoInventoryFile(Long inventoryVersionId, String path, RepoFileType fileType, long sizeBytes,
                             String contentHash, String indexedSummary) {
        this.inventoryVersionId = inventoryVersionId;
        this.path = path;
        this.fileType = fileType;
        this.sizeBytes = sizeBytes;
        this.contentHash = contentHash;
        this.indexedSummary = indexedSummary;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getInventoryVersionId() { return inventoryVersionId; }
    public String getPath() { return path; }
    public RepoFileType getFileType() { return fileType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getContentHash() { return contentHash; }
    public String getIndexedSummary() { return indexedSummary; }
    public Instant getCreatedAt() { return createdAt; }
}
