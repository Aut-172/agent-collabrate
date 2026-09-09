package com.example.agentcollab.repository;

import com.example.agentcollab.domain.WorkflowMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WorkflowMemberRepository extends JpaRepository<WorkflowMember, Long> {
    Optional<WorkflowMember> findByWorkflowIdAndUserId(Long workflowId, Long userId);
}
