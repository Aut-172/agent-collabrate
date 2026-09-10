package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.dto.TaskBlockerDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class TaskBlockerService {
    private final TaskRepository tasks;
    private final WorkflowRepository workflows;
    private final ProjectAccessService access;
    private final TaskAssignmentRepository assignments;
    private final TaskDeliveryRepository deliveries;
    private final TaskBlockerRepository blockers;
    private final TaskPackageService taskPackages;

    public TaskBlockerService(TaskRepository tasks, WorkflowRepository workflows,
                              ProjectAccessService access, TaskAssignmentRepository assignments,
                              TaskDeliveryRepository deliveries, TaskBlockerRepository blockers,
                              TaskPackageService taskPackages) {
        this.tasks = tasks;
        this.workflows = workflows;
        this.access = access;
        this.assignments = assignments;
        this.deliveries = deliveries;
        this.blockers = blockers;
        this.taskPackages = taskPackages;
    }

    @Transactional
    public TaskBlockerDtos.BlockerResponse report(Long actorId, Long taskId,
                                                   TaskBlockerDtos.ReportRequest request) {
        Task task = findTaskForUpdate(taskId);
        Workflow workflow = findWorkflowForUpdate(task.getWorkflowId());
        access.requireMember(workflow.getProjectId(), actorId);
        TaskAssignment assignment = assignments.findCurrentForUpdate(taskId)
                .orElseThrow(() -> conflict("TASK_NOT_ASSIGNED", "Task 尚未分配负责人"));
        if (!assignment.getAssigneeUserId().equals(actorId)) {
            throw forbidden("TASK_ASSIGNEE_REQUIRED", "只有当前负责人可以报告 Task Blocker");
        }
        if (blockers.existsByTaskIdAndStatus(taskId, TaskBlockerStatus.OPEN)) {
            throw conflict("TASK_BLOCKER_ALREADY_OPEN", "Task 已有未解决的 Blocker");
        }
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw conflict("TASK_STATE_CONFLICT", "只有开发中的 Task 可以报告 Blocker");
        }
        if (request.deliveryId() != null) {
            TaskDelivery delivery = deliveries.findById(request.deliveryId())
                    .orElseThrow(() -> notFound("TASK_DELIVERY_NOT_FOUND", "TaskDelivery 不存在"));
            if (!taskId.equals(delivery.getTaskId())) {
                throw notFound("TASK_DELIVERY_NOT_FOUND", "TaskDelivery 不存在");
            }
        }

        TaskBlocker blocker = blockers.save(new TaskBlocker(taskId, request.deliveryId(), request.reasonCode(),
                request.summary(), request.details(), request.evidence(), request.question(), actorId));
        task.block();
        workflow.markNeedsAttention();
        tasks.save(task);
        workflows.save(workflow);
        return TaskBlockerDtos.BlockerResponse.from(blocker, task, workflow);
    }

    @Transactional(readOnly = true)
    public List<TaskBlockerDtos.BlockerResponse> list(Long actorId, Long taskId) {
        Task task = tasks.findById(taskId)
                .orElseThrow(() -> notFound("TASK_NOT_FOUND", "Task 不存在"));
        Workflow workflow = workflows.findById(task.getWorkflowId())
                .orElseThrow(() -> notFound("WORKFLOW_NOT_FOUND", "Workflow 不存在"));
        access.requireMember(workflow.getProjectId(), actorId);
        return blockers.findByTaskIdOrderByCreatedAtDesc(taskId).stream()
                .map(blocker -> TaskBlockerDtos.BlockerResponse.from(blocker, task, workflow))
                .toList();
    }

    @Transactional
    public TaskBlockerDtos.BlockerResponse resolve(Long actorId, Long taskId, Long blockerId,
                                                    TaskBlockerDtos.CloseRequest request) {
        return close(actorId, taskId, blockerId, request.resolution(), false);
    }

    @Transactional
    public TaskBlockerDtos.BlockerResponse cancel(Long actorId, Long taskId, Long blockerId,
                                                   TaskBlockerDtos.CloseRequest request) {
        return close(actorId, taskId, blockerId, request.resolution(), true);
    }

    private TaskBlockerDtos.BlockerResponse close(Long actorId, Long taskId, Long blockerId,
                                                   String resolution, boolean cancelled) {
        Task task = findTaskForUpdate(taskId);
        Workflow workflow = findWorkflowForUpdate(task.getWorkflowId());
        requireResolver(workflow, actorId);
        TaskBlocker blocker = blockers.findByIdForUpdate(blockerId)
                .orElseThrow(() -> notFound("TASK_BLOCKER_NOT_FOUND", "TaskBlocker 不存在"));
        if (!taskId.equals(blocker.getTaskId())) {
            throw notFound("TASK_BLOCKER_NOT_FOUND", "TaskBlocker 不存在");
        }
        if (blocker.getStatus() != TaskBlockerStatus.OPEN) {
            throw conflict("TASK_BLOCKER_ALREADY_CLOSED", "TaskBlocker 已关闭");
        }
        if (task.getStatus() != TaskStatus.BLOCKED) {
            throw conflict("TASK_STATE_CONFLICT", "Task 当前不在阻塞状态");
        }

        if (cancelled) blocker.cancel(actorId, resolution);
        else blocker.resolve(actorId, resolution);
        blockers.saveAndFlush(blocker);

        taskPackages.regenerate(task);
        if (!blockers.existsByWorkflowIdAndStatus(workflow.getId(), TaskBlockerStatus.OPEN)) {
            workflow.markHealthy();
            workflows.save(workflow);
        }
        return TaskBlockerDtos.BlockerResponse.from(blocker, task, workflow);
    }

    private void requireResolver(Workflow workflow, Long actorId) {
        ProjectMember member = access.requireMember(workflow.getProjectId(), actorId);
        if (!workflow.getCreatedBy().equals(actorId) && member.getProjectRole() != ProjectMember.Role.LEADER) {
            throw forbidden("TASK_BLOCKER_RESOLVE_FORBIDDEN", "只有 Workflow 创建者或项目 Leader 可以关闭 Blocker");
        }
    }

    private Task findTaskForUpdate(Long taskId) {
        return tasks.findByIdForUpdate(taskId)
                .orElseThrow(() -> notFound("TASK_NOT_FOUND", "Task 不存在"));
    }

    private Workflow findWorkflowForUpdate(Long workflowId) {
        return workflows.findByIdForUpdate(workflowId)
                .orElseThrow(() -> notFound("WORKFLOW_NOT_FOUND", "Workflow 不存在"));
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
