package com.example.agentcollab.domain;

import com.example.agentcollab.exception.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowStateMachineTest {
    private final WorkflowStateMachine stateMachine = new WorkflowStateMachine();

    @Test
    void followsTheFrozenForwardTransitions() {
        Workflow workflow = new Workflow(1L, "workflow", "description", IntentLevel.FEATURE,
                WorkflowCompletionMode.CI_REQUIRED, null, 1L);
        WorkflowStatus[] states = {
                WorkflowStatus.DESIGN_PROPOSED,
                WorkflowStatus.SPEC_PROPOSED,
                WorkflowStatus.SPEC_CONFIRMED,
                WorkflowStatus.BUILD_PLAN_PROPOSED,
                WorkflowStatus.PLAN_APPROVED,
                WorkflowStatus.TASKS_READY,
                WorkflowStatus.IN_PROGRESS,
                WorkflowStatus.DELIVERY_SUBMITTED,
                WorkflowStatus.CI_RUNNING,
                WorkflowStatus.CI_PASSED,
                WorkflowStatus.READY_TO_CLOSE,
                WorkflowStatus.DONE
        };

        for (WorkflowStatus state : states) stateMachine.transition(workflow, state);

        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.DONE);
    }

    @Test
    void rejectsSkippedTransitionAndCancellingTerminalWorkflow() {
        Workflow workflow = new Workflow(1L, "workflow", "description", IntentLevel.FEATURE,
                WorkflowCompletionMode.CI_REQUIRED, null, 1L);

        assertThatThrownBy(() -> stateMachine.transition(workflow, WorkflowStatus.DONE))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("INVALID_WORKFLOW_TRANSITION");

        stateMachine.cancel(workflow);
        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.CANCELLED);
        assertThatThrownBy(() -> stateMachine.cancel(workflow))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void usesIntentSpecificPathsWithoutAddingNewStatuses() {
        Workflow change = new Workflow(1L, "change", "description", IntentLevel.CHANGE,
                WorkflowCompletionMode.CI_REQUIRED, null, 1L);
        stateMachine.transition(change, WorkflowStatus.BUILD_PLAN_PROPOSED);
        assertThatThrownBy(() -> stateMachine.transition(change, WorkflowStatus.SPEC_PROPOSED))
                .isInstanceOf(ApiException.class);
        stateMachine.transition(change, WorkflowStatus.PLAN_APPROVED);
        stateMachine.transition(change, WorkflowStatus.TASKS_READY);

        Workflow architecture = new Workflow(1L, "architecture", "description",
                IntentLevel.ARCHITECTURE, WorkflowCompletionMode.ARCHITECTURE_BASELINE, null, 1L);
        WorkflowStatus[] planningStates = {
                WorkflowStatus.DESIGN_PROPOSED,
                WorkflowStatus.SPEC_PROPOSED,
                WorkflowStatus.SPEC_CONFIRMED,
                WorkflowStatus.BUILD_PLAN_PROPOSED,
                WorkflowStatus.PLAN_APPROVED
        };
        for (WorkflowStatus state : planningStates) stateMachine.transition(architecture, state);
        assertThatThrownBy(() -> stateMachine.transition(architecture, WorkflowStatus.TASKS_READY))
                .isInstanceOf(ApiException.class);
        stateMachine.transition(architecture, WorkflowStatus.READY_TO_CLOSE);
        stateMachine.transition(architecture, WorkflowStatus.DONE);
        assertThat(architecture.getStatus()).isEqualTo(WorkflowStatus.DONE);
    }
}
