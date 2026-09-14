package com.example.agentcollab.repository;

import com.example.agentcollab.domain.DocumentDecision;
import com.example.agentcollab.domain.DocumentDecisionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentDecisionRepository extends JpaRepository<DocumentDecision, Long> {
    List<DocumentDecision> findByDocumentVersionIdInOrderByDocumentVersionIdAscDecisionKeyAsc(
            Collection<Long> documentVersionIds);
    List<DocumentDecision> findByDocumentVersionIdAndStatusOrderByDecisionKey(
            Long documentVersionId, DocumentDecisionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DocumentDecision d where d.id = :id")
    Optional<DocumentDecision> findByIdForUpdate(@Param("id") Long id);
}
