package com.example.agentcollab.repository;

import com.example.agentcollab.domain.TaskDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TaskDeliveryRepository extends JpaRepository<TaskDelivery, Long> {
    List<TaskDelivery> findByTaskIdOrderBySubmittedAtDesc(Long taskId);
    Optional<TaskDelivery> findTopByTaskIdOrderBySubmittedAtDesc(Long taskId);
    Optional<TaskDelivery> findByTaskIdAndCommitSha(Long taskId, String commitSha);
}
