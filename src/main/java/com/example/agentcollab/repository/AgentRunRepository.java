package com.example.agentcollab.repository;

import com.example.agentcollab.domain.AgentRun;
import com.example.agentcollab.domain.AgentRunStatus;
import com.example.agentcollab.domain.AgentRunType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AgentRunRepository extends JpaRepository<AgentRun, Long> {
    Optional<AgentRun> findTopByWorkflowIdAndRunTypeAndStatusInOrderByCreatedAtDesc(
            Long workflowId, AgentRunType runType, Collection<AgentRunStatus> statuses);
    List<AgentRun> findByWorkflowIdAndStatusIn(Long workflowId, Collection<AgentRunStatus> statuses);
    List<AgentRun> findByWorkflowIdOrderByCreatedAtDesc(Long workflowId);
    List<AgentRun> findByWorkflowIdInOrderByCreatedAtDesc(Collection<Long> workflowIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AgentRun r where r.id = :id")
    Optional<AgentRun> findByIdForUpdate(@Param("id") Long id);
}
