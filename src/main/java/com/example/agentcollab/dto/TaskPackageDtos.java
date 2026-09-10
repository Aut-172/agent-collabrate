package com.example.agentcollab.dto;

import com.example.agentcollab.domain.TaskPackage;
import com.example.agentcollab.domain.TaskPackageStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public final class TaskPackageDtos {
    private TaskPackageDtos() {}
    public record PackageResponse(Long id, Long taskId, int packageVersion, TaskPackageStatus status,
                                   String contentMarkdown, JsonNode contentJson, String contentHash,
                                   Long sourceTaskVersion, int sourcePlanVersion, Integer sourceSpecVersion,
                                   Integer sourceProfileVersion, String baseCommit, Instant createdAt, Long supersededBy) {
        public static PackageResponse from(TaskPackage p) {
            return new PackageResponse(p.getId(), p.getTaskId(), p.getPackageVersion(), p.getStatus(),
                    p.getContentMarkdown(), p.getContentJson(), p.getContentHash(), p.getSourceTaskVersion(),
                    p.getSourcePlanVersion(), p.getSourceSpecVersion(), p.getSourceProfileVersion(),
                    p.getBaseCommit(), p.getCreatedAt(), p.getSupersededBy());
        }
    }
    public record PackageDiffResponse(int fromVersion, int toVersion, String fromHash, String toHash,
                                      String fromMarkdown, String toMarkdown) {}
}
