package com.example.agentcollab.repository;

import com.example.agentcollab.domain.CodeContextStatus;
import com.example.agentcollab.domain.CodeContextVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface CodeContextVersionRepository extends JpaRepository<CodeContextVersion, Long> {
    Optional<CodeContextVersion> findTopByProjectIdAndStatusOrderByCreatedAtDesc(Long projectId, CodeContextStatus status);
    Optional<CodeContextVersion> findTopByContextPlanIdAndStatusOrderByCreatedAtDesc(Long contextPlanId, CodeContextStatus status);
    Optional<CodeContextVersion> findTopByContextPlanIdOrderByCreatedAtDesc(Long contextPlanId);
    List<CodeContextVersion> findByProjectIdAndStatus(Long projectId, CodeContextStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CodeContextVersion c where c.id = :id")
    Optional<CodeContextVersion> findByIdForUpdate(@Param("id") Long id);
}
