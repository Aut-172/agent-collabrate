package com.example.agentcollab.dto;

import java.time.Instant;

public final class ArchiveDtos {
    private ArchiveDtos() {}

    public record ArchiveFile(String key, String category, String fileName, String title,
                               Long workflowId, Long taskId, Integer versionNo,
                               String contentFormat, long size, Instant createdAt) {}
}
