package com.example.agentcollab.domain;

public enum TaskBlockerReason {
    REQUIREMENT_CLARIFICATION,
    SPEC_CONFLICT,
    DEPENDENCY,
    ENVIRONMENT,
    PERMISSION,
    CI_FAILURE,
    TASK_PACKAGE_UPDATED,
    OTHER
}
