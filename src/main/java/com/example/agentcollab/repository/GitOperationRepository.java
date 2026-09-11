package com.example.agentcollab.repository;

import com.example.agentcollab.domain.GitOperation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface GitOperationRepository extends JpaRepository<GitOperation, Long> {
    Optional<GitOperation> findByDeliveryId(Long deliveryId);
    List<GitOperation> findByTaskIdOrderByCreatedAtDesc(Long taskId);
    Optional<GitOperation> findTopByTaskIdOrderByCreatedAtDesc(Long taskId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GitOperation g where g.id = :id")
    Optional<GitOperation> findByIdForUpdate(@Param("id") Long id);
}
