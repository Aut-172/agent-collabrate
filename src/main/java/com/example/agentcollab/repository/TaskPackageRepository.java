package com.example.agentcollab.repository;

import com.example.agentcollab.domain.TaskPackage;
import com.example.agentcollab.domain.TaskPackageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TaskPackageRepository extends JpaRepository<TaskPackage, Long> {
    Optional<TaskPackage> findByTaskIdAndStatus(Long taskId, TaskPackageStatus status);
    Optional<TaskPackage> findByTaskIdAndPackageVersion(Long taskId, int packageVersion);
    List<TaskPackage> findByTaskIdOrderByPackageVersionDesc(Long taskId);
    Optional<TaskPackage> findTopByTaskIdOrderByPackageVersionDesc(Long taskId);
}
