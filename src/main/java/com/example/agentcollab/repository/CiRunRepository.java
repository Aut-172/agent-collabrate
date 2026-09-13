package com.example.agentcollab.repository;

import com.example.agentcollab.domain.CiRun;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CiRunRepository extends JpaRepository<CiRun, Long> {
    Optional<CiRun> findByDeliveryIdAndCommitSha(Long deliveryId, String commitSha);
    List<CiRun> findByTaskIdOrderByCreatedAtDesc(Long taskId);
    List<CiRun> findByWorkflowId(Long workflowId);
    Optional<CiRun> findTopByTaskIdOrderByCreatedAtDesc(Long taskId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CiRun c where c.id = :id")
    Optional<CiRun> findByIdForUpdate(@Param("id") Long id);
}
