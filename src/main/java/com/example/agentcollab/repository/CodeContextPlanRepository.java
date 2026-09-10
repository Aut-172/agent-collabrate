package com.example.agentcollab.repository;

import com.example.agentcollab.domain.CodeContextPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CodeContextPlanRepository extends JpaRepository<CodeContextPlan, Long> {
    Optional<CodeContextPlan> findTopByWorkflowIdOrderByCreatedAtDesc(Long workflowId);
}
