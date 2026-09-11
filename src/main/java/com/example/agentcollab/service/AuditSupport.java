package com.example.agentcollab.service;

import java.util.Map;

/** Null-safe bridge so legacy unit constructors can run without the audit collaborator. */
public final class AuditSupport {
    private AuditSupport() {}
    public static void record(AuditLogService audit, Long actor, Long project, String action,
                              String entityType, Long entityId, Map<String, ?> details) {
        if (audit != null) audit.record(actor, project, action, entityType, entityId, null, details);
    }
}
