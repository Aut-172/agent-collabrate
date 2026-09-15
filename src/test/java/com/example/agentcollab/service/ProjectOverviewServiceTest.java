package com.example.agentcollab.service;

import com.example.agentcollab.domain.IntentLevel;
import com.example.agentcollab.domain.Project;
import com.example.agentcollab.domain.ProjectCiStatus;
import com.example.agentcollab.domain.ProjectMember;
import com.example.agentcollab.domain.Task;
import com.example.agentcollab.domain.TaskAssignment;
import com.example.agentcollab.domain.TaskStatus;
import com.example.agentcollab.domain.User;
import com.example.agentcollab.domain.Workflow;
import com.example.agentcollab.domain.WorkflowCompletionMode;
import com.example.agentcollab.domain.WorkflowStatus;
import com.example.agentcollab.dto.ProjectOverviewDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.ProjectMemberRepository;
import com.example.agentcollab.repository.ProjectRepository;
import com.example.agentcollab.repository.TaskAssignmentRepository;
import com.example.agentcollab.repository.TaskRepository;
import com.example.agentcollab.repository.UserRepository;
import com.example.agentcollab.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectOverviewServiceTest {
    @Mock ProjectRepository projects;
    @Mock ProjectAccessService access;
    @Mock WorkflowRepository workflows;
    @Mock TaskRepository tasks;
    @Mock TaskAssignmentRepository assignments;
    @Mock ProjectMemberRepository members;
    @Mock UserRepository users;

    private ProjectOverviewService service;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ProjectOverviewService(projects, access, workflows, tasks, assignments, members, users);
    }

    @Test
    void aggregatesStatusesEffortMembersAndDailyTrends() {
        Project project = project(7L);
        Workflow openWorkflow = workflow(21L, WorkflowStatus.IN_PROGRESS,
                instant("2026-09-02T10:00:00Z"), instant("2026-09-02T10:00:00Z"));
        Workflow doneWorkflow = workflow(22L, WorkflowStatus.DONE,
                instant("2026-09-04T10:00:00Z"), instant("2026-09-04T10:00:00Z"));
        Task openTask = task(31L, 21L, 5, TaskStatus.IN_PROGRESS, instant("2026-09-02T11:00:00Z"));
        Task doneTask = task(32L, 22L, 3, TaskStatus.DONE, instant("2026-09-03T11:00:00Z"));
        ProjectMember leader = member(1L, ProjectMember.Role.LEADER, 10, "FULL_TIME");
        ProjectMember contributor = member(2L, ProjectMember.Role.MEMBER, 20, "PART_TIME");
        User leaderUser = user(1L, "leader");
        User contributorUser = user(2L, "contributor");

        when(access.requireMember(7L, 99L)).thenReturn(leader);
        when(projects.findById(7L)).thenReturn(Optional.of(project));
        when(workflows.findByProjectIdOrderByCreatedAtAsc(7L)).thenReturn(List.of(openWorkflow, doneWorkflow));
        when(tasks.findByWorkflowIdInOrderById(List.of(21L, 22L))).thenReturn(List.of(openTask, doneTask));
        TaskAssignment openAssignment = assignment(31L, 1L);
        TaskAssignment doneAssignment = assignment(32L, 2L);
        when(assignments.findByTaskIdInAndCurrentTrue(List.of(31L, 32L)))
                .thenReturn(List.of(openAssignment, doneAssignment));
        when(members.findByProjectIdAndStatus(7L, ProjectMember.Status.ACTIVE))
                .thenReturn(List.of(leader, contributor));
        when(users.findAllById(List.of(1L, 2L))).thenReturn(List.of(leaderUser, contributorUser));

        ProjectOverviewDtos.OverviewResponse result = service.get(99L, 7L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5));

        assertThat(result.project().activeWorkflowCount()).isEqualTo(1);
        assertThat(result.project().openTaskCount()).isEqualTo(1);
        assertThat(result.project().openEffortPoints()).isEqualTo(5);
        assertThat(result.workflows().byStatus()).containsEntry(WorkflowStatus.IN_PROGRESS, 1L)
                .containsEntry(WorkflowStatus.DONE, 1L);
        assertThat(result.tasks().byStatus()).containsEntry(TaskStatus.IN_PROGRESS, 1L)
                .containsEntry(TaskStatus.DONE, 1L);
        assertThat(result.workflows().dailyCreated()).extracting(ProjectOverviewDtos.DailyCount::count)
                .containsExactly(0L, 1L, 0L, 1L, 0L);
        assertThat(result.tasks().dailyCompleted()).extracting(ProjectOverviewDtos.DailyCount::count)
                .containsExactly(0L, 0L, 1L, 0L, 0L);
        assertThat(result.members()).extracting(ProjectOverviewDtos.MemberWorkload::username)
                .containsExactly("leader", "contributor");
        assertThat(result.members().get(0).openEffortPoints()).isEqualTo(5);
        assertThat(result.members().get(0).workloadRatio()).isEqualTo(0.5);
        assertThat(result.members().get(1).completedTaskCount()).isEqualTo(1);
        assertThat(result.members().get(1).workloadRatio()).isEqualTo(0.0);
    }

    @Test
    void returnsEmptyAggregatesForProjectWithoutWorkflows() {
        Project project = project(7L);
        ProjectMember member = member(1L, ProjectMember.Role.LEADER, null, null);
        when(access.requireMember(7L, 1L)).thenReturn(member);
        when(projects.findById(7L)).thenReturn(Optional.of(project));
        when(workflows.findByProjectIdOrderByCreatedAtAsc(7L)).thenReturn(List.of());
        when(members.findByProjectIdAndStatus(7L, ProjectMember.Status.ACTIVE)).thenReturn(List.of(member));
        when(users.findAllById(List.of(1L))).thenReturn(List.of(user(1L, "leader")));

        ProjectOverviewDtos.OverviewResponse result = service.get(1L, 7L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1));

        assertThat(result.workflows().total()).isZero();
        assertThat(result.tasks().total()).isZero();
        assertThat(result.members()).singleElement().satisfies(value -> {
            assertThat(value.assignedTaskCount()).isZero();
            assertThat(value.workloadRatio()).isNull();
        });
        verify(tasks, never()).findByWorkflowIdInOrderById(any());
        verify(assignments, never()).findByTaskIdInAndCurrentTrue(any());
    }

    @Test
    void startsTrendTimelineAtProjectCreationDate() {
        Project project = project(7L);
        ReflectionTestUtils.setField(project, "createdAt", instant("2026-09-03T12:00:00Z"));
        Workflow workflow = workflow(21L, WorkflowStatus.INTENT,
                instant("2026-09-04T10:00:00Z"), instant("2026-09-04T10:00:00Z"));
        when(access.requireMember(7L, 1L)).thenReturn(member(1L, ProjectMember.Role.LEADER, null, null));
        when(projects.findById(7L)).thenReturn(Optional.of(project));
        when(workflows.findByProjectIdOrderByCreatedAtAsc(7L)).thenReturn(List.of(workflow));
        when(tasks.findByWorkflowIdInOrderById(List.of(21L))).thenReturn(List.of());
        when(members.findByProjectIdAndStatus(7L, ProjectMember.Status.ACTIVE)).thenReturn(List.of());
        when(users.findAllById(List.of())).thenReturn(List.of());

        ProjectOverviewDtos.OverviewResponse result = service.get(1L, 7L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5));

        assertThat(result.from()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(result.workflows().dailyCreated()).extracting(ProjectOverviewDtos.DailyCount::date)
                .containsExactly(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 5));
        assertThat(result.workflows().dailyCreated()).extracting(ProjectOverviewDtos.DailyCount::count)
                .containsExactly(0L, 1L, 0L);
    }

    @Test
    void rejectsUnauthorizedAccessAndInvalidDateRanges() {
        ApiException unauthorized = new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                "PROJECT_NOT_FOUND", "项目不存在或无权访问");
        when(access.requireMember(7L, 8L)).thenThrow(unauthorized);
        assertThatThrownBy(() -> service.get(8L, 7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1)))
                .isSameAs(unauthorized);
        verify(projects, never()).findById(any());

        Project project = project(7L);
        when(access.requireMember(7L, 1L)).thenReturn(member(1L, ProjectMember.Role.LEADER, null, null));
        when(projects.findById(7L)).thenReturn(Optional.of(project));
        assertThatThrownBy(() -> service.get(1L, 7L, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("统计时间范围无效");
    }

    private Project project(Long id) {
        Project project = new Project("project", "https://example.test/repo", "github", "main", 1L);
        ReflectionTestUtils.setField(project, "id", id);
        ReflectionTestUtils.setField(project, "createdAt", instant("2026-08-01T00:00:00Z"));
        ReflectionTestUtils.setField(project, "ciStatus", ProjectCiStatus.CI_REQUIRED);
        return project;
    }

    private Workflow workflow(Long id, WorkflowStatus status, Instant createdAt, Instant updatedAt) {
        Workflow workflow = new Workflow(7L, "workflow-" + id, "description", IntentLevel.FEATURE,
                WorkflowCompletionMode.CI_REQUIRED, null, 1L);
        ReflectionTestUtils.setField(workflow, "id", id);
        ReflectionTestUtils.setField(workflow, "status", status);
        ReflectionTestUtils.setField(workflow, "createdAt", createdAt);
        ReflectionTestUtils.setField(workflow, "updatedAt", updatedAt);
        return workflow;
    }

    private Task task(Long id, Long workflowId, int effort, TaskStatus status, Instant updatedAt) {
        Task task = new Task(workflowId, "TASK-" + id, "task-" + id, "description", effort,
                1, 1, "feature/task-" + id);
        ReflectionTestUtils.setField(task, "id", id);
        ReflectionTestUtils.setField(task, "status", status);
        ReflectionTestUtils.setField(task, "updatedAt", updatedAt);
        return task;
    }

    private ProjectMember member(Long userId, ProjectMember.Role role, Integer capacity, String availability) {
        ProjectMember member = new ProjectMember(7L, userId, role);
        ReflectionTestUtils.setField(member, "status", ProjectMember.Status.ACTIVE);
        ReflectionTestUtils.setField(member, "weeklyCapacityPoints", capacity);
        ReflectionTestUtils.setField(member, "availability", availability);
        return member;
    }

    private User user(Long id, String username) {
        User user = new User(username, "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private TaskAssignment assignment(Long taskId, Long assigneeId) {
        return new TaskAssignment(taskId, assigneeId, 99L, 1, "fit", BigDecimal.ONE, 1,
                json.createObjectNode(), json.createObjectNode());
    }

    private Instant instant(String value) { return Instant.parse(value); }
}
