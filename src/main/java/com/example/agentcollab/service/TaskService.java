package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.dto.PlanDtos;
import com.example.agentcollab.dto.TaskDtos;
import com.example.agentcollab.dto.WorkflowDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TaskService {
    private final TaskRepository tasks;
    private final TaskAssignmentRepository assignments;
    private final DocumentVersionRepository documents;
    private final ProjectMemberRepository members;
    private final UserRepository users;
    private final WorkflowRepository workflows;
    private final ProjectRepository projects;
    private final WorkflowService workflowService;
    private final ProjectAccessService access;
    private final WorkflowStateMachine stateMachine;
    private final PlanService plans;
    private final ObjectMapper json;
    private final TaskPackageService taskPackages;
    private final NotificationService notifications;
    private final AuditLogService audit;

    public TaskService(TaskRepository tasks, TaskAssignmentRepository assignments,
                       DocumentVersionRepository documents, ProjectMemberRepository members,
                       UserRepository users, WorkflowRepository workflows, ProjectRepository projects,
                       WorkflowService workflowService, ProjectAccessService access,
                       WorkflowStateMachine stateMachine, PlanService plans, ObjectMapper json,
                       TaskPackageService taskPackages, NotificationService notifications, AuditLogService audit) {
        this.tasks = tasks;
        this.assignments = assignments;
        this.documents = documents;
        this.members = members;
        this.users = users;
        this.workflows = workflows;
        this.projects = projects;
        this.workflowService = workflowService;
        this.access = access;
        this.stateMachine = stateMachine;
        this.plans = plans;
        this.json = json;
        this.taskPackages = taskPackages;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Transactional
    public PlanDtos.CreationResponse createFromApprovedPlan(Long actorId, Long workflowId) {
        Workflow workflow = workflowService.requireForUpdate(actorId, workflowId);
        access.requireLeader(workflow.getProjectId(), actorId);
        workflowService.requireActiveProject(workflow);
        if (workflow.getIntentLevel() == IntentLevel.ARCHITECTURE
                && workflow.getStatus() == WorkflowStatus.READY_TO_CLOSE) {
            return creationResponse(workflow);
        }
        if (workflow.getIntentLevel() != IntentLevel.ARCHITECTURE
                && workflow.getStatus() == WorkflowStatus.TASKS_READY) {
            return creationResponse(workflow);
        }
        if (workflow.getStatus() != WorkflowStatus.PLAN_APPROVED) {
            throw conflict("WORKFLOW_STATE_CONFLICT", "只有已批准的 Build Plan 可以创建任务");
        }
        requireDevelopmentEnabled(workflow);

        DocumentVersion approvedPlan = latestApprovedPlan(workflowId);
        JsonNode plan = plans.validateForWorkflow(workflow, approvedPlan.getContent());
        if (workflow.getIntentLevel() == IntentLevel.ARCHITECTURE) {
            createChildIntents(actorId, workflow, plan);
            stateMachine.transition(workflow, WorkflowStatus.READY_TO_CLOSE);
        } else {
            createDevelopmentTasks(actorId, workflow, approvedPlan, plan);
            stateMachine.transition(workflow, WorkflowStatus.TASKS_READY);
        }
        workflows.save(workflow);
        return creationResponse(workflow);
    }

    @Transactional(readOnly = true)
    public List<TaskDtos.TaskResponse> list(Long actorId, Long workflowId) {
        workflowService.get(actorId, workflowId);
        return tasks.findByWorkflowIdOrderById(workflowId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TaskDtos.TaskResponse get(Long actorId, Long taskId) {
        Task task = find(taskId);
        workflowService.get(actorId, task.getWorkflowId());
        return toResponse(task);
    }

    @Transactional
    public TaskDtos.TaskResponse reassign(Long actorId, Long taskId, TaskDtos.ReassignRequest request) {
        Task task = tasks.findByIdForUpdate(taskId)
                .orElseThrow(() -> notFound("TASK_NOT_FOUND", "Task 不存在"));
        Workflow workflow = workflowService.get(actorId, task.getWorkflowId());
        access.requireLeader(workflow.getProjectId(), actorId);
        workflowService.requireActiveProject(workflow);
        if (task.isTerminal()) throw conflict("TASK_NOT_ASSIGNABLE", "终态 Task 不能重新分配");

        int nextVersion = assignments.findTopByTaskIdOrderByAssignmentVersionDesc(taskId)
                .map(value -> value.getAssignmentVersion() + 1).orElse(1);
        assignments.findCurrentForUpdate(taskId).ifPresent(TaskAssignment::end);
        assignments.flush();
        TaskAssignment assignment = createAssignment(task, workflow.getProjectId(), request.assigneeUserId(), actorId,
                nextVersion, request.reason(), request.assignmentScore());
        task.markAssigned();
        tasks.saveAndFlush(task);
        taskPackages.regenerate(task);
        notifyAssignment(task, assignment, true);
        AuditSupport.record(audit, actorId, workflow.getProjectId(), "TASK_REASSIGNED", "TASK", taskId, Map.of("assigneeUserId", assignment.getAssigneeUserId()));
        return toResponse(task);
    }

    private void createDevelopmentTasks(Long actorId, Workflow workflow,
                                        DocumentVersion approvedPlan, JsonNode plan) {
        Integer specVersion = latestConfirmedSpecVersion(workflow);
        Map<String, Task> created = new LinkedHashMap<>();
        for (JsonNode taskNode : plan.path("tasks")) {
            Task task = tasks.save(new Task(workflow.getId(), taskNode.path("taskKey").asText(),
                    taskNode.path("title").asText(), taskNode.path("description").asText(),
                    taskNode.path("effortPoints").asInt(), approvedPlan.getVersionNo(), specVersion,
                    taskNode.path("branchName").asText()));
            AuditSupport.record(audit, actorId, workflow.getProjectId(), "TASK_CREATED", "TASK", task.getId(), Map.of("externalKey", task.getExternalKey()));
            created.put(task.getExternalKey(), task);
        }
        for (JsonNode assignment : plan.path("assignments")) {
            Task task = created.get(assignment.path("taskKey").asText());
            boolean ciBootstrap = workflow.getCompletionMode() == WorkflowCompletionMode.CI_BOOTSTRAP;
            Long assigneeUserId = ciBootstrap ? workflow.getCreatedBy() : assignment.path("userId").asLong();
            String reason = ciBootstrap
                    ? "工程与 CI 初始化任务由创建该 Workflow 的项目 Leader 直接负责"
                    : assignment.path("fitReason").asText();
            BigDecimal score = ciBootstrap ? null : assignment.path("assignmentScore").decimalValue();
            TaskAssignment createdAssignment = createAssignment(task, workflow.getProjectId(), assigneeUserId, actorId,
                    1, reason, score);
            task.markAssigned();
            tasks.save(task);
            taskPackages.createInitial(task);
            notifyAssignment(task, createdAssignment, false);
        }
    }

    private void createChildIntents(Long actorId, Workflow workflow, JsonNode plan) {
        for (JsonNode child : plan.path("childIntents")) {
            IntentLevel childLevel = IntentLevel.valueOf(child.path("intentLevel").asText());
            if (childLevel == IntentLevel.ARCHITECTURE) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHILD_INTENT_LEVEL",
                        "Architecture 只能创建 Feature 或 Change 子 Intent，不能嵌套 Architecture");
            }
            workflowService.create(actorId, workflow.getProjectId(), new WorkflowDtos.CreateWorkflowRequest(
                    child.path("title").asText(), child.path("description").asText(),
                    childLevel, workflow.getId(), null, true));
        }
    }

    private void requireDevelopmentEnabled(Workflow workflow) {
        if (workflow.getIntentLevel() == IntentLevel.ARCHITECTURE
                || workflow.getCompletionMode() == WorkflowCompletionMode.CI_BOOTSTRAP) {
            return;
        }
        Project project = projectsForWorkflow(workflow);
        if (project.getCiStatus() != ProjectCiStatus.CI_REQUIRED) {
            throw conflict("CI_BOOTSTRAP_REQUIRED", "项目必须先完成 CI Bootstrap 才能创建普通开发任务");
        }
    }

    private Project projectsForWorkflow(Workflow workflow) {
        return projects.findById(workflow.getProjectId())
                .orElseThrow(() -> notFound("PROJECT_NOT_FOUND", "项目不存在"));
    }

    private TaskAssignment createAssignment(Task task, Long projectId, Long assigneeUserId, Long actorId,
                                              int version, String reason, BigDecimal score) {
        ProjectMember member = requireAssignableMember(projectId, assigneeUserId);
        ObjectNode workload = json.createObjectNode();
        workload.put("calculation", "SUM_CURRENT_NON_TERMINAL_TASK_EFFORT_POINTS");
        workload.putArray("includedStatuses").add("ASSIGNED").add("IN_PROGRESS").add("BLOCKED")
                .add("DELIVERY_SUBMITTED").add("CI_RUNNING");
        workload.put("openEffortPoints", tasks.sumOpenEffortPoints(projectId, assigneeUserId));
        workload.put("weeklyCapacityPoints", member.getWeeklyCapacityPoints());
        workload.put("availability", member.getAvailability());
        return assignments.save(new TaskAssignment(task.getId(), assigneeUserId, actorId, version, reason, score,
                member.getProfileVersion(), member.getCapabilityProfile(), workload));
    }

    private void notifyAssignment(Task task, TaskAssignment assignment, boolean reassigned) {
        notifications.notifyUser(assignment.getAssigneeUserId(),
                reassigned ? "TASK_REASSIGNED" : "TASK_ASSIGNED", "TASK", task.getId(),
                reassigned ? "任务已重新分配给你" : "你收到一个新任务",
                task.getExternalKey() + "：" + task.getTitle());
    }

    private ProjectMember requireAssignableMember(Long projectId, Long userId) {
        ProjectMember member = members.findByProjectIdAndUserIdForUpdate(projectId, userId)
                .filter(value -> value.getStatus() == ProjectMember.Status.ACTIVE)
                .orElseThrow(() -> conflict("ASSIGNEE_NOT_ACTIVE", "负责人不是当前项目的有效成员"));
        if (!users.findById(userId).map(User::isEnabled).orElse(false)
                || !member.isProfileCompleted() || member.getCapabilityProfile() == null
                || member.getWeeklyCapacityPoints() == null
                || member.getAvailability() == null || member.getAvailability().isBlank()) {
            throw conflict("ASSIGNEE_NOT_ASSIGNABLE", "负责人尚未完成可分配画像");
        }
        return member;
    }

    private Integer latestConfirmedSpecVersion(Workflow workflow) {
        if (workflow.getIntentLevel() == IntentLevel.CHANGE) return null;
        DocumentVersion spec = documents.findTopByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(
                        workflow.getId(), DocumentType.SPEC)
                .orElseThrow(() -> conflict("DOCUMENT_NOT_FOUND", "当前 Workflow 缺少 Spec"));
        if (!spec.isConfirmed()) throw conflict("DOCUMENT_NOT_CONFIRMED", "当前 Spec 尚未确认");
        return spec.getVersionNo();
    }

    private DocumentVersion latestApprovedPlan(Long workflowId) {
        DocumentVersion plan = documents.findTopByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(
                        workflowId, DocumentType.BUILD_PLAN)
                .orElseThrow(() -> conflict("DOCUMENT_NOT_FOUND", "当前 Workflow 缺少 Build Plan"));
        if (!plan.isConfirmed()) throw conflict("DOCUMENT_NOT_CONFIRMED", "当前 Build Plan 尚未批准");
        return plan;
    }

    private TaskDtos.TaskResponse toResponse(Task task) {
        JsonNode details = planTaskDetails(task);
        TaskAssignment assignment = assignments.findByTaskIdAndCurrentTrue(task.getId()).orElse(null);
        return TaskDtos.TaskResponse.from(task, details, assignment);
    }

    private JsonNode planTaskDetails(Task task) {
        DocumentVersion plan = documents.findByWorkflowIdAndDocumentTypeAndVersionNo(
                        task.getWorkflowId(), DocumentType.BUILD_PLAN, task.getSourcePlanVersion())
                .orElseThrow(() -> new IllegalStateException("Approved Build Plan is missing"));
        try {
            for (JsonNode node : json.readTree(plan.getContent()).path("tasks")) {
                if (task.getExternalKey().equals(node.path("taskKey").asText())) return node.deepCopy();
            }
            return null;
        } catch (Exception ex) {
            throw new IllegalStateException("Approved Build Plan cannot be parsed", ex);
        }
    }

    private PlanDtos.CreationResponse creationResponse(Workflow workflow) {
        return new PlanDtos.CreationResponse(tasks.findByWorkflowIdOrderById(workflow.getId()).size(),
                workflows.findByParentWorkflowIdOrderById(workflow.getId()).size(), workflow.getStatus());
    }

    private Task find(Long taskId) {
        return tasks.findById(taskId).orElseThrow(() -> notFound("TASK_NOT_FOUND", "Task 不存在"));
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
