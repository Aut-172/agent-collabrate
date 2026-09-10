package com.example.agentcollab.dto;

import com.example.agentcollab.domain.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public final class CodeContextDtos {
    private CodeContextDtos() {}

    public record SyncResponse(Long runId, CodeContextRunStatus status, String statusUrl) {
        public static SyncResponse from(CodeContextRun run) {
            return new SyncResponse(run.getId(), run.getStatus(),
                    "/api/projects/" + run.getProjectId() + "/code-context/runs/" + run.getId());
        }
    }

    public record RunResponse(Long id, Long projectId, String runType, CodeContextRunStatus status,
                              Long inventoryVersionId, Long contextPlanId, Long codeContextVersionId,
                              String errorMessage, Instant createdAt,
                              Instant startedAt, Instant finishedAt) {
        public static RunResponse from(CodeContextRun run) {
            return new RunResponse(run.getId(), run.getProjectId(), run.getRunType().name(), run.getStatus(),
                    run.getInventoryVersionId(), run.getContextPlanId(), run.getCodeContextVersionId(),
                    run.getErrorMessage(), run.getCreatedAt(),
                    run.getStartedAt(), run.getFinishedAt());
        }
    }

    public record InventoryFileResponse(String path, RepoFileType fileType, long sizeBytes,
                                        String contentHash, String indexedSummary) {
        public static InventoryFileResponse from(RepoInventoryFile file) {
            return new InventoryFileResponse(file.getPath(), file.getFileType(), file.getSizeBytes(),
                    file.getContentHash(), file.getIndexedSummary());
        }
    }

    public record InventoryResponse(Long id, Long projectId, String provider, String repositoryUrl,
                                    String branchName, String commitSha, RepoInventoryStatus status,
                                    JsonNode repositoryProfile, JsonNode treeSummary,
                                    List<InventoryFileResponse> files, Instant createdAt, Instant updatedAt) {}

    public record EvidenceFileResponse(String path, CodeEvidenceType evidenceType, String contentHash,
                                       String summary, String excerpt) {
        public static EvidenceFileResponse from(CodeContextFile file) {
            return new EvidenceFileResponse(file.getPath(), file.getEvidenceType(), file.getContentHash(),
                    file.getSummary(), file.getExcerpt());
        }
    }

    public record ContextResponse(Long id, Long projectId, Long inventoryVersionId, Long contextPlanId,
                                  String provider, String repositoryUrl, String branchName, String baseCommitSha,
                                  CodeContextStatus status, JsonNode repositoryProfile, JsonNode evidence,
                                  List<EvidenceFileResponse> files, Instant createdAt, Instant updatedAt) {}
}
