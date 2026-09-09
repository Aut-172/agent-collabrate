package com.example.agentcollab.repository;

import com.example.agentcollab.domain.DocumentType;
import com.example.agentcollab.domain.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    List<DocumentVersion> findByWorkflowIdOrderByCreatedAtDesc(Long workflowId);
    List<DocumentVersion> findByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(Long workflowId, DocumentType type);
    Optional<DocumentVersion> findTopByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(Long workflowId, DocumentType type);
    Optional<DocumentVersion> findByWorkflowIdAndDocumentTypeAndVersionNo(Long workflowId, DocumentType type, int versionNo);
}
