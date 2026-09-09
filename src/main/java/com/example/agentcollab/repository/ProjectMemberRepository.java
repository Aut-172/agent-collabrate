package com.example.agentcollab.repository;

import com.example.agentcollab.domain.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {
    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);
    List<ProjectMember> findByProjectIdAndStatus(Long projectId, ProjectMember.Status status);
    List<ProjectMember> findByUserIdAndStatus(Long userId, ProjectMember.Status status);
}
