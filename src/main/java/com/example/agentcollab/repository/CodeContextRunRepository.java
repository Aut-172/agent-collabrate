package com.example.agentcollab.repository;

import com.example.agentcollab.domain.CodeContextRun;
import com.example.agentcollab.domain.CodeContextRunStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.Optional;

public interface CodeContextRunRepository extends JpaRepository<CodeContextRun, Long> {
    Optional<CodeContextRun> findTopByProjectIdAndRunTypeAndStatusInOrderByCreatedAtDesc(
            Long projectId, CodeContextRun.Type runType, Collection<CodeContextRunStatus> statuses);

    Optional<CodeContextRun> findTopByProjectIdAndRunTypeOrderByCreatedAtDesc(
            Long projectId, CodeContextRun.Type runType);

    Optional<CodeContextRun> findTopByContextPlanIdAndRunTypeOrderByCreatedAtDesc(
            Long contextPlanId, CodeContextRun.Type runType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CodeContextRun r where r.id = :id")
    Optional<CodeContextRun> findByIdForUpdate(@Param("id") Long id);
}
