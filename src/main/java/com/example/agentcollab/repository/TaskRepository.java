package com.example.agentcollab.repository;

import com.example.agentcollab.domain.Task;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByWorkflowIdOrderById(Long workflowId);
    Optional<Task> findByWorkflowIdAndExternalKey(Long workflowId, String externalKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    Optional<Task> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            SELECT COALESCE(SUM(t.effort_points), 0)
            FROM tasks t
            JOIN workflows w ON w.id = t.workflow_id
            JOIN task_assignments a ON a.task_id = t.id AND a.is_current = TRUE
            WHERE w.project_id = :projectId
              AND a.assignee_user_id = :userId
              AND t.status IN ('ASSIGNED', 'IN_PROGRESS', 'BLOCKED', 'DELIVERY_SUBMITTED', 'CI_RUNNING')
            """, nativeQuery = true)
    int sumOpenEffortPoints(@Param("projectId") Long projectId, @Param("userId") Long userId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM tasks t
                JOIN workflows w ON w.id = t.workflow_id
                JOIN task_assignments a ON a.task_id = t.id AND a.is_current = TRUE
                WHERE w.project_id = :projectId
                  AND a.assignee_user_id = :userId
                  AND t.status NOT IN ('DONE', 'FAILED', 'CANCELLED')
            )
            """, nativeQuery = true)
    boolean existsOpenTaskForAssignee(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
