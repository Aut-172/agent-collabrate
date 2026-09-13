package com.example.agentcollab.domain;

import com.example.agentcollab.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.EnumMap;
import java.util.Map;

@Component
public class WorkflowStateMachine {
    private static final Map<WorkflowStatus, WorkflowStatus> DEFAULT_NEXT = new EnumMap<>(WorkflowStatus.class);

    static {
        DEFAULT_NEXT.put(WorkflowStatus.INTENT, WorkflowStatus.DESIGN_PROPOSED);
        DEFAULT_NEXT.put(WorkflowStatus.DESIGN_PROPOSED, WorkflowStatus.DESIGN_CONFIRMED);
        DEFAULT_NEXT.put(WorkflowStatus.DESIGN_CONFIRMED, WorkflowStatus.SPEC_PROPOSED);
        DEFAULT_NEXT.put(WorkflowStatus.SPEC_PROPOSED, WorkflowStatus.SPEC_CONFIRMED);
        DEFAULT_NEXT.put(WorkflowStatus.SPEC_CONFIRMED, WorkflowStatus.BUILD_PLAN_PROPOSED);
        DEFAULT_NEXT.put(WorkflowStatus.BUILD_PLAN_PROPOSED, WorkflowStatus.PLAN_APPROVED);
        DEFAULT_NEXT.put(WorkflowStatus.PLAN_APPROVED, WorkflowStatus.TASKS_READY);
        DEFAULT_NEXT.put(WorkflowStatus.TASKS_READY, WorkflowStatus.IN_PROGRESS);
        DEFAULT_NEXT.put(WorkflowStatus.IN_PROGRESS, WorkflowStatus.DELIVERY_SUBMITTED);
        DEFAULT_NEXT.put(WorkflowStatus.DELIVERY_SUBMITTED, WorkflowStatus.CI_RUNNING);
        DEFAULT_NEXT.put(WorkflowStatus.CI_RUNNING, WorkflowStatus.CI_PASSED);
        DEFAULT_NEXT.put(WorkflowStatus.CI_PASSED, WorkflowStatus.READY_TO_CLOSE);
        DEFAULT_NEXT.put(WorkflowStatus.READY_TO_CLOSE, WorkflowStatus.DONE);
    }

    public void transition(Workflow workflow, WorkflowStatus target) {
        if (next(workflow) != target) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_WORKFLOW_TRANSITION",
                    "不允许从 " + workflow.getStatus() + " 转换到 " + target);
        }
        workflow.transitionTo(target);
    }

    private WorkflowStatus next(Workflow workflow) {
        if (workflow.getIntentLevel() == IntentLevel.CHANGE && workflow.getStatus() == WorkflowStatus.INTENT) {
            return WorkflowStatus.BUILD_PLAN_PROPOSED;
        }
        if (workflow.getIntentLevel() == IntentLevel.ARCHITECTURE
                && workflow.getStatus() == WorkflowStatus.PLAN_APPROVED) {
            return WorkflowStatus.READY_TO_CLOSE;
        }
        return DEFAULT_NEXT.get(workflow.getStatus());
    }

    public void cancel(Workflow workflow) {
        if (workflow.getStatus() == WorkflowStatus.DONE
                || workflow.getStatus() == WorkflowStatus.CANCELLED
                || workflow.getStatus() == WorkflowStatus.FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_WORKFLOW_TRANSITION",
                    "当前状态不允许取消 Workflow");
        }
        workflow.transitionTo(WorkflowStatus.CANCELLED);
    }
}
