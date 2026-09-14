package com.example.agentcollab.repository;

import com.example.agentcollab.domain.AgentCallRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentCallRecordRepository extends JpaRepository<AgentCallRecord, Long> {
    long countByAgentRunId(Long agentRunId);
    List<AgentCallRecord> findByAgentRunIdOrderByAttemptNoAsc(Long agentRunId);
}
