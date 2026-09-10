package com.example.agentcollab.repository;

import com.example.agentcollab.domain.CodeContextStatus;
import com.example.agentcollab.domain.CodeContextVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CodeContextVersionRepository extends JpaRepository<CodeContextVersion, Long> {
    Optional<CodeContextVersion> findTopByProjectIdAndStatusOrderByCreatedAtDesc(Long projectId, CodeContextStatus status);
    Optional<CodeContextVersion> findTopByContextPlanIdAndStatusOrderByCreatedAtDesc(Long contextPlanId, CodeContextStatus status);
    List<CodeContextVersion> findByProjectIdAndStatus(Long projectId, CodeContextStatus status);
}
