package com.example.agentcollab.repository;

import com.example.agentcollab.domain.TaskBlocker;
import com.example.agentcollab.domain.TaskBlockerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface TaskBlockerRepository extends JpaRepository<TaskBlocker, Long> {
    List<TaskBlocker> findByTaskIdOrderByCreatedAtDesc(Long taskId);
    Optional<TaskBlocker> findByTaskIdAndStatus(Long taskId, TaskBlockerStatus status);
    boolean existsByTaskIdAndStatus(Long taskId, TaskBlockerStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from TaskBlocker b where b.id = :id")
    Optional<TaskBlocker> findByIdForUpdate(@Param("id") Long id);

    @Query("select case when count(b) > 0 then true else false end from TaskBlocker b, Task t "
            + "where b.taskId = t.id and t.workflowId = :workflowId and b.status = :status")
    boolean existsByWorkflowIdAndStatus(@Param("workflowId") Long workflowId,
                                        @Param("status") TaskBlockerStatus status);
}
