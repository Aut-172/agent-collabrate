package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.dto.WorkflowBoardDtos;
import com.example.agentcollab.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowBoardServiceTest {
    @Mock WorkflowService workflows;
    @Mock TaskRepository tasks;
    @Mock TaskAssignmentRepository assignments;
    @Mock TaskDeliveryRepository deliveries;
    @Mock GitOperationRepository gitOperations;
    @Mock CiRunRepository ciRuns;
    @Mock UserRepository users;
    @Mock WorkflowRepository workflowRepository;
    @Mock ProjectAccessService access;

    @Test
    void createsStableEightColumnsAndKeepsTerminalTasksVisible() {
        Workflow workflow = new Workflow(3L, "workflow", "description", IntentLevel.CHANGE,
                WorkflowCompletionMode.CI_BOOTSTRAP, null, 1L);
        ReflectionTestUtils.setField(workflow, "id", 5L);
        Task done = task("done", TaskStatus.DONE);
        Task cancelled = task("cancelled", TaskStatus.CANCELLED);
        Task failed = task("failed", TaskStatus.FAILED);
        when(workflows.get(1L, 5L)).thenReturn(workflow);
        when(tasks.findByWorkflowIdOrderById(5L)).thenReturn(List.of(done, cancelled, failed));
        when(assignments.findByTaskIdAndCurrentTrue(org.mockito.ArgumentMatchers.anyLong())).thenReturn(java.util.Optional.empty());
        when(deliveries.findTopByTaskIdOrderBySubmittedAtDesc(org.mockito.ArgumentMatchers.anyLong())).thenReturn(java.util.Optional.empty());
        when(gitOperations.findTopByTaskIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.anyLong())).thenReturn(java.util.Optional.empty());
        when(ciRuns.findTopByTaskIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.anyLong())).thenReturn(java.util.Optional.empty());

        var board = new WorkflowBoardService(workflows, tasks, assignments, deliveries, gitOperations, ciRuns, users)
                .get(1L, 5L);

        assertThat(board.columns()).extracting(WorkflowBoardDtos.BoardColumn::key)
                .containsExactly("TODO", "ASSIGNED", "IN_PROGRESS", "BLOCKED", "DELIVERY_SUBMITTED",
                        "CI_RUNNING", "DONE", "CANCELLED_OR_FAILED");
        assertThat(board.columns().get(6).cards()).extracting(WorkflowBoardDtos.TaskCard::taskKey)
                .containsExactly("done");
        assertThat(board.columns().get(7).cards()).extracting(WorkflowBoardDtos.TaskCard::taskKey)
                .containsExactlyInAnyOrder("cancelled", "failed");
    }

    @Test
    void projectBoardAggregatesTasksAcrossAllProjectMembers() {
        Workflow workflow = new Workflow(7L, "workflow", "description", IntentLevel.FEATURE,
                WorkflowCompletionMode.CI_REQUIRED, null, 1L);
        ReflectionTestUtils.setField(workflow, "id", 5L);
        Task memberTask = task("member-task", TaskStatus.ASSIGNED);
        ReflectionTestUtils.setField(memberTask, "workflowId", 5L);
        Task otherTask = task("other-task", TaskStatus.TODO);
        ReflectionTestUtils.setField(otherTask, "workflowId", 5L);
        TaskAssignment current = org.mockito.Mockito.mock(TaskAssignment.class);
        when(current.getAssigneeUserId()).thenReturn(2L);
        when(workflowRepository.findByProjectIdOrderByCreatedAtAsc(7L)).thenReturn(List.of(workflow));
        when(tasks.findByWorkflowIdInOrderById(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(memberTask, otherTask));
        when(assignments.findByTaskIdAndCurrentTrue(memberTask.getId())).thenReturn(Optional.of(current));
        when(assignments.findByTaskIdAndCurrentTrue(otherTask.getId())).thenReturn(Optional.empty());
        when(deliveries.findTopByTaskIdOrderBySubmittedAtDesc(org.mockito.ArgumentMatchers.anyLong())).thenReturn(Optional.empty());
        when(gitOperations.findTopByTaskIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.anyLong())).thenReturn(Optional.empty());
        when(ciRuns.findTopByTaskIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.anyLong())).thenReturn(Optional.empty());

        var board = new WorkflowBoardService(workflows, tasks, assignments, deliveries, gitOperations, ciRuns, users,
                workflowRepository, access).getForProject(1L, 7L);

        assertThat(board.projectId()).isEqualTo(7L);
        assertThat(board.columns().get(1).cards()).extracting(WorkflowBoardDtos.TaskCard::taskKey)
                .containsExactly("member-task");
        assertThat(board.columns().get(0).cards()).extracting(WorkflowBoardDtos.TaskCard::taskKey)
                .containsExactly("other-task");
    }

    private Task task(String key, TaskStatus status) {
        Task task = new Task(5L, key, key, "description", 1, 1, null, "feature/" + key);
        ReflectionTestUtils.setField(task, "id", (long) key.hashCode());
        ReflectionTestUtils.setField(task, "status", status);
        return task;
    }
}
