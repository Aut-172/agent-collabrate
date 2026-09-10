package com.example.agentcollab.domain;

public enum OutboxJobType {
    AGENT_RUN,
    CODE_CONTEXT_SYNC,
    GIT_SYNC,
    CI_SYNC
}
