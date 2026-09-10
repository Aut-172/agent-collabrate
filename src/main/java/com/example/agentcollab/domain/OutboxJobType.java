package com.example.agentcollab.domain;

public enum OutboxJobType {
    AGENT_RUN,
    CODE_CONTEXT_SYNC,
    CODE_CONTEXT_EVIDENCE,
    GIT_SYNC,
    CI_SYNC
}
