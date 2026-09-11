package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.dto.WorkflowBoardDtos;
import com.example.agentcollab.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WorkflowBoardService {
    private static final List<ColumnSpec> COLUMNS = List.of(
            new ColumnSpec("TODO", "待处理", Set.of(TaskStatus.TODO)),
            new ColumnSpec("ASSIGNED", "已分配", Set.of(TaskStatus.ASSIGNED)),
            new ColumnSpec("IN_PROGRESS", "开发中", Set.of(TaskStatus.IN_PROGRESS)),
            new ColumnSpec("BLOCKED", "阻塞", Set.of(TaskStatus.BLOCKED)),
            new ColumnSpec("DELIVERY_SUBMITTED", "待交付", Set.of(TaskStatus.DELIVERY_SUBMITTED)),
            new ColumnSpec("CI_RUNNING", "CI 中", Set.of(TaskStatus.CI_RUNNING)),
            new ColumnSpec("DONE", "已完成", Set.of(TaskStatus.DONE)),
            new ColumnSpec("CANCELLED_OR_FAILED", "已取消/失败", Set.of(TaskStatus.CANCELLED, TaskStatus.FAILED)));
    private final WorkflowService workflows;
    private final TaskRepository tasks;
    private final TaskAssignmentRepository assignments;
    private final TaskDeliveryRepository deliveries;
    private final GitOperationRepository gitOperations;
    private final CiRunRepository ciRuns;
    private final UserRepository users;

    public WorkflowBoardService(WorkflowService workflows, TaskRepository tasks,
                                TaskAssignmentRepository assignments, TaskDeliveryRepository deliveries,
                                GitOperationRepository gitOperations, CiRunRepository ciRuns, UserRepository users) {
        this.workflows = workflows; this.tasks = tasks; this.assignments = assignments;
        this.deliveries = deliveries; this.gitOperations = gitOperations; this.ciRuns = ciRuns; this.users = users;
    }

    @Transactional(readOnly = true)
    public WorkflowBoardDtos.BoardResponse get(Long actorId, Long workflowId) {
        Workflow workflow = workflows.get(actorId, workflowId);
        Map<TaskStatus, List<WorkflowBoardDtos.TaskCard>> cards = tasks.findByWorkflowIdOrderById(workflowId).stream()
                .map(task -> card(task, workflow)).collect(Collectors.groupingBy(
                        WorkflowBoardDtos.TaskCard::status, LinkedHashMap::new, Collectors.toList()));
        List<WorkflowBoardDtos.BoardColumn> columns = COLUMNS.stream()
                .map(spec -> new WorkflowBoardDtos.BoardColumn(spec.key, spec.label,
                        spec.statuses.stream().flatMap(status -> cards.getOrDefault(status, List.of()).stream()).toList()))
                .toList();
        return new WorkflowBoardDtos.BoardResponse(workflow.getId(), workflow.getTitle(), columns);
    }

    private WorkflowBoardDtos.TaskCard card(Task task, Workflow workflow) {
        WorkflowBoardDtos.Assignee assignee = assignments.findByTaskIdAndCurrentTrue(task.getId())
                .map(a -> users.findById(a.getAssigneeUserId())
                        .map(u -> new WorkflowBoardDtos.Assignee(u.getId(), u.getUsername()))
                        .orElse(new WorkflowBoardDtos.Assignee(a.getAssigneeUserId(), null)))
                .orElse(null);
        TaskDelivery delivery = deliveries.findTopByTaskIdOrderBySubmittedAtDesc(task.getId()).orElse(null);
        GitOperation git = gitOperations.findTopByTaskIdOrderByCreatedAtDesc(task.getId()).orElse(null);
        CiRun ci = ciRuns.findTopByTaskIdOrderByCreatedAtDesc(task.getId()).orElse(null);
        return new WorkflowBoardDtos.TaskCard(task.getId(), task.getExternalKey(), task.getExternalKey(), task.getTitle(), task.getStatus(),
                assignee, workflow.getId(), workflow.getTitle(), task.getCurrentPackageVersion(), task.getBranchName(),
                delivery == null ? null : delivery.getCommitSha(), delivery == null ? null : delivery.getPullRequestUrl(),
                git == null ? null : git.getStatus().name(), ci == null ? null : ci.getStatus().name(),
                ci == null ? null : ci.getDetailsUrl(), task.getUpdatedAt());
    }

    private record ColumnSpec(String key, String label, Set<TaskStatus> statuses) {}
}
