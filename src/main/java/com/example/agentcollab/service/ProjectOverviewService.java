package com.example.agentcollab.service;

import com.example.agentcollab.domain.IntentLevel;
import com.example.agentcollab.domain.Project;
import com.example.agentcollab.domain.ProjectMember;
import com.example.agentcollab.domain.Task;
import com.example.agentcollab.domain.TaskAssignment;
import com.example.agentcollab.domain.TaskStatus;
import com.example.agentcollab.domain.Workflow;
import com.example.agentcollab.domain.WorkflowStatus;
import com.example.agentcollab.dto.ProjectOverviewDtos;
import com.example.agentcollab.repository.ProjectMemberRepository;
import com.example.agentcollab.repository.ProjectRepository;
import com.example.agentcollab.repository.TaskAssignmentRepository;
import com.example.agentcollab.repository.TaskRepository;
import com.example.agentcollab.repository.UserRepository;
import com.example.agentcollab.repository.WorkflowRepository;
import com.example.agentcollab.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProjectOverviewService {
    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final int MAX_RANGE_DAYS = 366;
    private final ProjectRepository projects;
    private final ProjectAccessService access;
    private final WorkflowRepository workflows;
    private final TaskRepository tasks;
    private final TaskAssignmentRepository assignments;
    private final ProjectMemberRepository members;
    private final UserRepository users;

    public ProjectOverviewService(ProjectRepository projects, ProjectAccessService access,
                                  WorkflowRepository workflows, TaskRepository tasks,
                                  TaskAssignmentRepository assignments, ProjectMemberRepository members,
                                  UserRepository users) {
        this.projects = projects;
        this.access = access;
        this.workflows = workflows;
        this.tasks = tasks;
        this.assignments = assignments;
        this.members = members;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public ProjectOverviewDtos.OverviewResponse get(Long actorId, Long projectId,
                                                     LocalDate requestedFrom, LocalDate requestedTo) {
        access.requireMember(projectId, actorId);
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在"));
        LocalDate to = requestedTo == null ? LocalDate.now(ZoneOffset.UTC) : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(DEFAULT_RANGE_DAYS - 1L) : requestedFrom;
        if (from.isAfter(to) || from.plusDays(MAX_RANGE_DAYS - 1L).isBefore(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OVERVIEW_RANGE", "统计时间范围无效，最多查询 366 天");
        }
        LocalDate projectCreatedDate = project.getCreatedAt() == null ? null : utcDate(project.getCreatedAt());
        if (projectCreatedDate != null && projectCreatedDate.isAfter(from)) {
            from = projectCreatedDate;
        }
        final LocalDate effectiveFrom = from;

        List<Workflow> projectWorkflows = workflows.findByProjectIdOrderByCreatedAtAsc(projectId);
        List<Long> workflowIds = projectWorkflows.stream().map(Workflow::getId).toList();
        List<Task> projectTasks = workflowIds.isEmpty()
                ? List.of() : tasks.findByWorkflowIdInOrderById(workflowIds);
        List<Long> taskIds = projectTasks.stream().map(Task::getId).toList();
        Map<Long, TaskAssignment> currentAssignments = taskIds.isEmpty()
                ? Map.of() : assignments.findByTaskIdInAndCurrentTrue(taskIds).stream()
                .collect(Collectors.toMap(TaskAssignment::getTaskId, Function.identity(), (first, ignored) -> first));

        Map<WorkflowStatus, Long> workflowStatuses = enumCounts(WorkflowStatus.class);
        Map<IntentLevel, Long> intentLevels = enumCounts(IntentLevel.class);
        projectWorkflows.forEach(workflow -> {
            increment(workflowStatuses, workflow.getStatus());
            increment(intentLevels, workflow.getIntentLevel());
        });

        Map<TaskStatus, Long> taskStatuses = enumCounts(TaskStatus.class);
        projectTasks.forEach(task -> increment(taskStatuses, task.getStatus()));
        List<ProjectOverviewDtos.DailyCount> workflowCreated = dailyCounts(effectiveFrom, to,
                projectWorkflows.stream().map(Workflow::getCreatedAt).toList());
        List<ProjectOverviewDtos.DailyCount> taskCompleted = dailyCounts(effectiveFrom, to,
                projectTasks.stream().filter(task -> task.getStatus() == TaskStatus.DONE)
                        .map(Task::getUpdatedAt).toList());

        List<ProjectMember> projectMembers = members.findByProjectIdAndStatus(projectId, ProjectMember.Status.ACTIVE);
        Map<Long, MemberAccumulator> workload = new LinkedHashMap<>();
        projectMembers.forEach(member -> workload.put(member.getUserId(), new MemberAccumulator()));
        projectTasks.forEach(task -> {
            TaskAssignment assignment = currentAssignments.get(task.getId());
            if (assignment == null) return;
            MemberAccumulator member = workload.get(assignment.getAssigneeUserId());
            if (member == null) return;
            member.assignedTaskCount++;
            if (isOpen(task.getStatus())) {
                member.openTaskCount++;
                member.openEffortPoints += task.getEffortPoints();
            }
            if (task.getStatus() == TaskStatus.DONE && inRange(task.getUpdatedAt(), effectiveFrom, to)) {
                member.completedTaskCount++;
            }
        });
        Map<Long, String> usernames = users.findAllById(projectMembers.stream().map(ProjectMember::getUserId).toList())
                .stream().collect(Collectors.toMap(com.example.agentcollab.domain.User::getId,
                        com.example.agentcollab.domain.User::getUsername));
        List<ProjectOverviewDtos.MemberWorkload> memberStats = projectMembers.stream()
                .map(member -> {
                    MemberAccumulator value = workload.get(member.getUserId());
                    Integer capacity = member.getWeeklyCapacityPoints();
                    Double ratio = capacity == null || capacity <= 0 ? null
                            : (double) value.openEffortPoints / capacity;
                    return new ProjectOverviewDtos.MemberWorkload(member.getUserId(),
                            usernames.getOrDefault(member.getUserId(), "unknown"), member.getProjectRole().name(),
                            value.assignedTaskCount, value.openTaskCount, value.openEffortPoints,
                            value.completedTaskCount, capacity, member.getAvailability(), ratio);
                })
                .sorted(java.util.Comparator.comparing(
                        ProjectOverviewDtos.MemberWorkload::workloadRatio,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                        .thenComparing(ProjectOverviewDtos.MemberWorkload::username))
                .toList();

        long openTaskCount = projectTasks.stream().filter(task -> isOpen(task.getStatus())).count();
        int openEffort = projectTasks.stream().filter(task -> isOpen(task.getStatus()))
                .mapToInt(Task::getEffortPoints).sum();
        long activeWorkflowCount = projectWorkflows.stream()
                .filter(workflow -> !isTerminal(workflow.getStatus())).count();
        return new ProjectOverviewDtos.OverviewResponse(effectiveFrom, to,
                new ProjectOverviewDtos.ProjectSummary(project.getId(), project.getCreatedAt(),
                        project.getCiStatus(), activeWorkflowCount, openTaskCount, openEffort),
                new ProjectOverviewDtos.WorkflowStats(projectWorkflows.size(), workflowStatuses, intentLevels,
                        workflowCreated),
                new ProjectOverviewDtos.TaskStats(projectTasks.size(), taskStatuses, openTaskCount, openEffort,
                        taskCompleted), memberStats);
    }

    private <E extends Enum<E>> Map<E, Long> enumCounts(Class<E> type) {
        Map<E, Long> result = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) result.put(value, 0L);
        return result;
    }

    private <E extends Enum<E>> void increment(Map<E, Long> counts, E value) {
        counts.computeIfPresent(value, (ignored, count) -> count + 1L);
    }

    private List<ProjectOverviewDtos.DailyCount> dailyCounts(LocalDate from, LocalDate to, List<Instant> timestamps) {
        Map<LocalDate, Long> counts = new HashMap<>();
        timestamps.stream().filter(Objects::nonNull).map(this::utcDate)
                .filter(date -> !date.isBefore(from) && !date.isAfter(to))
                .forEach(date -> counts.merge(date, 1L, Long::sum));
        List<ProjectOverviewDtos.DailyCount> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            result.add(new ProjectOverviewDtos.DailyCount(date, counts.getOrDefault(date, 0L)));
        }
        return result;
    }

    private LocalDate utcDate(Instant instant) { return instant.atZone(ZoneOffset.UTC).toLocalDate(); }

    private boolean inRange(Instant timestamp, LocalDate from, LocalDate to) {
        if (timestamp == null) return false;
        LocalDate date = utcDate(timestamp);
        return !date.isBefore(from) && !date.isAfter(to);
    }

    private boolean isOpen(TaskStatus status) {
        return status != TaskStatus.DONE && status != TaskStatus.FAILED && status != TaskStatus.CANCELLED;
    }

    private boolean isTerminal(WorkflowStatus status) {
        return status == WorkflowStatus.DONE || status == WorkflowStatus.CANCELLED || status == WorkflowStatus.FAILED;
    }

    private static final class MemberAccumulator {
        private int assignedTaskCount;
        private int openTaskCount;
        private int openEffortPoints;
        private int completedTaskCount;
    }
}
