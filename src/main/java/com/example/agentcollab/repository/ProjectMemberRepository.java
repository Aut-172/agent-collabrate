package com.example.agentcollab.repository;

import com.example.agentcollab.domain.ProjectMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {
    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);
    List<ProjectMember> findByProjectIdAndStatus(Long projectId, ProjectMember.Status status);
    List<ProjectMember> findByUserIdAndStatus(Long userId, ProjectMember.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ProjectMember m where m.projectId = :projectId and m.userId = :userId")
    Optional<ProjectMember> findByProjectIdAndUserIdForUpdate(
            @Param("projectId") Long projectId, @Param("userId") Long userId);
}
