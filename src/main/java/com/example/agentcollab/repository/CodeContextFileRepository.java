package com.example.agentcollab.repository;

import com.example.agentcollab.domain.CodeContextFile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CodeContextFileRepository extends JpaRepository<CodeContextFile, Long> {
    List<CodeContextFile> findByContextVersionIdOrderByPath(Long contextVersionId);
}
