package com.example.agentcollab.dto;

import com.example.agentcollab.domain.AuditLog;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public final class AuditLogDtos {
    private AuditLogDtos() {}
    public record AuditLogResponse(Long id, Long actorUserId, Long projectId, String action,
                                   String entityType, Long entityId, String requestId,
                                   JsonNode details, Instant createdAt) {
        public static AuditLogResponse from(AuditLog a) {
            return new AuditLogResponse(a.getId(), a.getActorUserId(), a.getProjectId(), a.getAction(),
                    a.getEntityType(), a.getEntityId(), a.getRequestId(), a.getDetailsJson(), a.getCreatedAt());
        }
    }
}
