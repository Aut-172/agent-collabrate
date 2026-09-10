package com.example.agentcollab.repository;

import com.example.agentcollab.domain.RepoInventoryStatus;
import com.example.agentcollab.domain.RepoInventoryVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RepoInventoryVersionRepository extends JpaRepository<RepoInventoryVersion, Long> {
    Optional<RepoInventoryVersion> findByProjectIdAndProviderAndBranchNameAndCommitSha(
            Long projectId, String provider, String branchName, String commitSha);
    Optional<RepoInventoryVersion> findTopByProjectIdAndStatusOrderByCreatedAtDesc(
            Long projectId, RepoInventoryStatus status);
    List<RepoInventoryVersion> findByProjectIdAndStatus(Long projectId, RepoInventoryStatus status);
}
