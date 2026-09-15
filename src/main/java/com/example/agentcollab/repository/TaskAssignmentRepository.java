package com.example.agentcollab.repository;

import com.example.agentcollab.domain.TaskAssignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {
    Optional<TaskAssignment> findByTaskIdAndCurrentTrue(Long taskId);
    List<TaskAssignment> findByTaskIdInAndCurrentTrue(Collection<Long> taskIds);
    List<TaskAssignment> findByTaskIdOrderByAssignmentVersionDesc(Long taskId);
    Optional<TaskAssignment> findTopByTaskIdOrderByAssignmentVersionDesc(Long taskId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from TaskAssignment a where a.taskId = :taskId and a.current = true")
    Optional<TaskAssignment> findCurrentForUpdate(@Param("taskId") Long taskId);
}
