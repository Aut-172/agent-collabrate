package com.example.agentcollab.repository;

import com.example.agentcollab.domain.OutboxJob;
import com.example.agentcollab.domain.OutboxJobStatus;
import com.example.agentcollab.domain.OutboxJobType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OutboxJobRepository extends JpaRepository<OutboxJob, Long> {
    Optional<OutboxJob> findByJobTypeAndReferenceId(OutboxJobType jobType, Long referenceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from OutboxJob j where j.jobType = :jobType and j.referenceId = :referenceId")
    Optional<OutboxJob> findByReferenceForUpdate(
            @Param("jobType") OutboxJobType jobType, @Param("referenceId") Long referenceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from OutboxJob j where j.id = :id")
    Optional<OutboxJob> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            SELECT * FROM outbox_jobs
            WHERE job_type = 'AGENT_RUN' AND status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<OutboxJob> findNextDueForUpdate(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from OutboxJob j where j.jobType = :jobType and j.status = :status and j.lockedAt < :deadline")
    List<OutboxJob> findTimedOutForUpdate(
            @Param("jobType") OutboxJobType jobType, @Param("status") OutboxJobStatus status,
            @Param("deadline") Instant deadline);
}
