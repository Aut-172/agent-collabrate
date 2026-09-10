package com.example.agentcollab.repository;

import com.example.agentcollab.domain.RepoInventoryFile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RepoInventoryFileRepository extends JpaRepository<RepoInventoryFile, Long> {
    List<RepoInventoryFile> findByInventoryVersionIdOrderByPath(Long inventoryVersionId);
}
