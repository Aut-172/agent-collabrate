package com.example.agentcollab.repository;

import com.example.agentcollab.domain.Workflow;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.example.agentcollab.domain.WorkflowCompletionMode;
import com.example.agentcollab.domain.WorkflowStatus;

public interface WorkflowRepository extends JpaRepository<Workflow, Long> {
    List<Workflow> findByProjectIdInOrderByUpdatedAtDesc(Collection<Long> projectIds);
    List<Workflow> findByParentWorkflowIdOrderById(Long parentWorkflowId);
    boolean existsByProjectIdAndCompletionModeAndStatusNotIn(
            Long projectId, WorkflowCompletionMode completionMode, Collection<WorkflowStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Workflow w where w.id = :id")
    Optional<Workflow> findByIdForUpdate(@Param("id") Long id);
}
