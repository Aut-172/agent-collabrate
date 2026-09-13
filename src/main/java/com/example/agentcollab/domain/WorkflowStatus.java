package com.example.agentcollab.domain;

public enum WorkflowStatus {
    INTENT,
    DESIGN_PROPOSED,
    DESIGN_CONFIRMED,
    SPEC_PROPOSED,
    SPEC_CONFIRMED,
    BUILD_PLAN_PROPOSED,
    PLAN_APPROVED,
    TASKS_READY,
    IN_PROGRESS,
    DELIVERY_SUBMITTED,
    CI_RUNNING,
    CI_PASSED,
    READY_TO_CLOSE,
    DONE,
    CANCELLED,
    FAILED
}
