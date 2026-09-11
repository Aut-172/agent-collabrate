package com.example.agentcollab.repository;

import com.example.agentcollab.domain.TaskDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TaskDeliveryRepository extends JpaRepository<TaskDelivery, Long> {
    List<TaskDelivery> findByTaskIdOrderBySubmittedAtDesc(Long taskId);
    Optional<TaskDelivery> findTopByTaskIdOrderBySubmittedAtDesc(Long taskId);
    Optional<TaskDelivery> findByTaskIdAndCommitSha(Long taskId, String commitSha);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT td.* FROM task_deliveries td
            JOIN tasks t ON t.id = td.task_id
            JOIN workflows w ON w.id = t.workflow_id
            JOIN projects p ON p.id = w.project_id
            WHERE p.id = :projectId AND lower(td.commit_sha) = lower(:commitSha)
            ORDER BY td.submitted_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<TaskDelivery> findLatestByProjectIdAndCommitSha(@org.springframework.data.repository.query.Param("projectId") Long projectId,
                                                               @org.springframework.data.repository.query.Param("commitSha") String commitSha);
}
