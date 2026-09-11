package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.dto.WorkflowDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.ProjectMemberRepository;
import com.example.agentcollab.repository.ProjectRepository;
import com.example.agentcollab.repository.TaskBlockerRepository;
import com.example.agentcollab.repository.WorkflowMemberRepository;
import com.example.agentcollab.repository.WorkflowRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
import java.util.Map;

@Service
public class WorkflowService {
    private static final Set<WorkflowStatus> TERMINAL_STATUSES = Set.of(
            WorkflowStatus.DONE, WorkflowStatus.CANCELLED, WorkflowStatus.FAILED);
    private final WorkflowRepository workflows;
    private final WorkflowMemberRepository workflowMembers;
    private final ProjectRepository projects;
    private final ProjectMemberRepository projectMembers;
    private final ProjectAccessService access;
    private final WorkflowStateMachine stateMachine;
    private final AgentRunCancellationService runCancellation;
    private final TaskCancellationService taskCancellation;
    private final TaskBlockerRepository blockers;
    private final AuditLogService audit;

    public WorkflowService(WorkflowRepository workflows, WorkflowMemberRepository workflowMembers,
                           ProjectRepository projects, ProjectMemberRepository projectMembers,
                           ProjectAccessService access, WorkflowStateMachine stateMachine,
                           AgentRunCancellationService runCancellation,
                           TaskCancellationService taskCancellation,
                           TaskBlockerRepository blockers, AuditLogService audit) {
        this.workflows = workflows;
        this.workflowMembers = workflowMembers;
        this.projects = projects;
        this.projectMembers = projectMembers;
        this.access = access;
        this.stateMachine = stateMachine;
        this.runCancellation = runCancellation;
        this.taskCancellation = taskCancellation;
        this.blockers = blockers;
        this.audit = audit;
    }

    @Transactional
    public Workflow create(Long actorId, Long projectId, WorkflowDtos.CreateWorkflowRequest request) {
        if (request.completionMode() == WorkflowCompletionMode.CI_BOOTSTRAP) {
            throw badRequest("CI_BOOTSTRAP_ENDPOINT_REQUIRED",
                    "工程与 CI 初始化必须使用专用 Bootstrap 接口");
        }
        return createInternal(actorId, projectId, request);
    }

    private Workflow createInternal(Long actorId, Long projectId, WorkflowDtos.CreateWorkflowRequest request) {
        access.requireMember(projectId, actorId);
        Project project = projects.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在"));
        if (project.getStatus() != Project.Status.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_ARCHIVED", "归档项目不能创建 Workflow");
        }
        Long parentWorkflowId = validateParent(projectId, request.parentWorkflowId());
        WorkflowCompletionMode completionMode = resolveCompletionMode(
                actorId, project, request.intentLevel(), request.completionMode());
        Workflow workflow = workflows.save(new Workflow(projectId, request.title(), request.description(),
                request.intentLevel(), completionMode, parentWorkflowId, actorId));
        workflowMembers.save(new WorkflowMember(workflow.getId(), actorId, WorkflowMember.Role.OWNER));
        AuditSupport.record(audit, actorId, projectId, "WORKFLOW_CREATED", "WORKFLOW", workflow.getId(), Map.of("intentLevel", request.intentLevel().name()));
        return workflow;
    }

    @Transactional
    public Workflow createCiBootstrap(Long actorId, Long projectId,
                                      WorkflowDtos.CreateCiBootstrapRequest request) {
        return createInternal(actorId, projectId, new WorkflowDtos.CreateWorkflowRequest(
                request.title(), request.description(), IntentLevel.FEATURE, null,
                WorkflowCompletionMode.CI_BOOTSTRAP));
    }

    @Transactional(readOnly = true)
    public Workflow get(Long actorId, Long workflowId) {
        Workflow workflow = find(workflowId);
        access.requireMember(workflow.getProjectId(), actorId);
        return workflow;
    }

    @Transactional(readOnly = true)
    public List<Workflow> listForUser(Long actorId) {
        List<Long> projectIds = projectMembers.findByUserIdAndStatus(actorId, ProjectMember.Status.ACTIVE).stream()
                .map(ProjectMember::getProjectId).toList();
        if (projectIds.isEmpty()) return List.of();
        return workflows.findByProjectIdInOrderByUpdatedAtDesc(projectIds);
    }

    @Transactional
    public Workflow cancel(Long actorId, Long workflowId) {
        Workflow workflow = requireForUpdate(actorId, workflowId);
        access.requireLeader(workflow.getProjectId(), actorId);
        stateMachine.cancel(workflow);
        runCancellation.cancelForWorkflow(workflowId);
        taskCancellation.cancelForWorkflow(workflowId);
        Workflow saved = workflows.save(workflow);
        AuditSupport.record(audit, actorId, workflow.getProjectId(), "WORKFLOW_CANCELLED", "WORKFLOW", workflowId, Map.of());
        return saved;
    }

    @Transactional
    public Workflow close(Long actorId, Long workflowId) {
        Workflow workflow = requireForUpdate(actorId, workflowId);
        access.requireLeader(workflow.getProjectId(), actorId);
        Project project = projects.findByIdForUpdate(workflow.getProjectId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在"));
        if (workflow.getStatus() != WorkflowStatus.READY_TO_CLOSE) {
            throw conflict("WORKFLOW_NOT_READY_TO_CLOSE", "Workflow 尚未满足关闭条件");
        }
        if (blockers.existsByWorkflowIdAndStatus(workflowId, TaskBlockerStatus.OPEN)) {
            throw conflict("WORKFLOW_HAS_OPEN_BLOCKERS", "Workflow 存在未解决的 Task Blocker");
        }
        if (workflow.getCompletionMode() == WorkflowCompletionMode.CI_BOOTSTRAP) {
            if (project.getCiStatus() != ProjectCiStatus.CI_NOT_CONFIGURED) {
                throw conflict("CI_ALREADY_CONFIGURED", "项目已经启用 CI 门禁");
            }
            project.enableCi();
            projects.save(project);
        } else if (workflow.getCompletionMode() == WorkflowCompletionMode.CI_REQUIRED
                && project.getCiStatus() != ProjectCiStatus.CI_REQUIRED) {
            throw conflict("CI_BOOTSTRAP_REQUIRED", "项目尚未完成 CI Bootstrap");
        }
        stateMachine.transition(workflow, WorkflowStatus.DONE);
        Workflow saved = workflows.save(workflow);
        AuditSupport.record(audit, actorId, workflow.getProjectId(), "WORKFLOW_CLOSED", "WORKFLOW", workflowId, Map.of());
        return saved;
    }

    public Workflow requireForUpdate(Long actorId, Long workflowId) {
        Workflow workflow = workflows.findByIdForUpdate(workflowId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WORKFLOW_NOT_FOUND", "Workflow 不存在"));
        access.requireMember(workflow.getProjectId(), actorId);
        return workflow;
    }

    public Workflow findForUpdate(Long workflowId) {
        return workflows.findByIdForUpdate(workflowId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WORKFLOW_NOT_FOUND", "Workflow 不存在"));
    }

    public void requireCreator(Workflow workflow, Long actorId) {
        if (!workflow.getCreatedBy().equals(actorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WORKFLOW_CREATOR_REQUIRED", "需要 Workflow 创建者权限");
        }
    }

    public void requireEditor(Workflow workflow, Long actorId) {
        ProjectMember member = access.requireMember(workflow.getProjectId(), actorId);
        if (!workflow.getCreatedBy().equals(actorId) && member.getProjectRole() != ProjectMember.Role.LEADER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DOCUMENT_EDIT_FORBIDDEN", "只有创建者或项目 Leader 可以编辑文档");
        }
    }

    public void requireActiveProject(Workflow workflow) {
        Project project = projects.findById(workflow.getProjectId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在"));
        if (project.getStatus() != Project.Status.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_ARCHIVED", "归档项目不能推进 Workflow");
        }
    }

    private Workflow find(Long workflowId) {
        return workflows.findById(workflowId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WORKFLOW_NOT_FOUND", "Workflow 不存在"));
    }

    private Long validateParent(Long projectId, Long parentWorkflowId) {
        if (parentWorkflowId == null) return null;
        Workflow parent = workflows.findById(parentWorkflowId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "PARENT_WORKFLOW_NOT_FOUND", "父 Workflow 不存在"));
        if (!parent.getProjectId().equals(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PARENT_WORKFLOW_NOT_FOUND", "父 Workflow 不存在");
        }
        return parent.getId();
    }

    private WorkflowCompletionMode resolveCompletionMode(Long actorId, Project project, IntentLevel intentLevel,
                                                          WorkflowCompletionMode requested) {
        if (intentLevel == IntentLevel.ARCHITECTURE) {
            if (requested != null && requested != WorkflowCompletionMode.ARCHITECTURE_BASELINE) {
                throw badRequest("INVALID_COMPLETION_MODE", "Architecture 只能使用 ARCHITECTURE_BASELINE");
            }
            return WorkflowCompletionMode.ARCHITECTURE_BASELINE;
        }
        if (requested == WorkflowCompletionMode.ARCHITECTURE_BASELINE) {
            throw badRequest("INVALID_COMPLETION_MODE", "Feature/Change 不能使用 ARCHITECTURE_BASELINE");
        }
        if (requested != WorkflowCompletionMode.CI_BOOTSTRAP) {
            return WorkflowCompletionMode.CI_REQUIRED;
        }
        access.requireLeader(project.getId(), actorId);
        if (project.getCiStatus() != ProjectCiStatus.CI_NOT_CONFIGURED) {
            throw conflict("CI_ALREADY_CONFIGURED", "项目已经启用 CI 门禁，不能创建 CI Bootstrap");
        }
        if (workflows.existsByProjectIdAndCompletionModeAndStatusNotIn(
                project.getId(), WorkflowCompletionMode.CI_BOOTSTRAP, TERMINAL_STATUSES)) {
            throw conflict("CI_BOOTSTRAP_EXISTS", "项目已有进行中的 CI Bootstrap");
        }
        return WorkflowCompletionMode.CI_BOOTSTRAP;
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
