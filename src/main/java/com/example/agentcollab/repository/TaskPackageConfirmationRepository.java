package com.example.agentcollab.repository;

import com.example.agentcollab.domain.TaskPackageConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TaskPackageConfirmationRepository extends JpaRepository<TaskPackageConfirmation, Long> {
    Optional<TaskPackageConfirmation> findByTaskIdAndUserIdAndPackageVersion(
            Long taskId, Long userId, int packageVersion);
    Optional<TaskPackageConfirmation> findTopByTaskIdAndUserIdOrderByCreatedAtDesc(Long taskId, Long userId);
}
