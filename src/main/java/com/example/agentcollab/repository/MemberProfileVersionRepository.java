package com.example.agentcollab.repository;

import com.example.agentcollab.domain.MemberProfileVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberProfileVersionRepository extends JpaRepository<MemberProfileVersion, Long> {
    long countByProjectMemberId(Long projectMemberId);
}
