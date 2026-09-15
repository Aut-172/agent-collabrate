package com.example.agentcollab.repository;

import com.example.agentcollab.domain.AgentCallRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;

public interface AgentCallRecordRepository extends JpaRepository<AgentCallRecord, Long> {
    long countByAgentRunId(Long agentRunId);
    List<AgentCallRecord> findByAgentRunIdOrderByAttemptNoAsc(Long agentRunId);
    List<AgentCallRecord> findByAgentRunIdInOrderByCreatedAtDesc(Collection<Long> agentRunIds);
}
