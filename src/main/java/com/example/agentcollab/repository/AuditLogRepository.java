package com.example.agentcollab.repository;

import com.example.agentcollab.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByProjectIdIn(Iterable<Long> projectIds, Pageable pageable);
    Page<AuditLog> findByProjectId(Long projectId, Pageable pageable);
    Page<AuditLog> findByProjectIdAndEntityTypeAndEntityId(Long projectId, String entityType, Long entityId, Pageable pageable);
}
