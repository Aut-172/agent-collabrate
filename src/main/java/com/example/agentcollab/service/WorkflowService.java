package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.dto.WorkflowDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.ProjectMemberRepository;
import com.example.agentcollab.repository.ProjectRepository;
import com.example.agentcollab.repository.WorkflowMemberRepository;
import com.example.agentcollab.repository.WorkflowRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class WorkflowService {
    private final WorkflowRepository workflows;
    private final WorkflowMemberRepository workflowMembers;
    private final ProjectRepository projects;
    private final ProjectMemberRepository projectMembers;
    private final ProjectAccessService access;
    private final WorkflowStateMachine stateMachine;
    private final AgentRunCancellationService runCancellation;

    public WorkflowService(WorkflowRepository workflows, WorkflowMemberRepository workflowMembers,
                           ProjectRepository projects, ProjectMemberRepository projectMembers,
                           ProjectAccessService access, WorkflowStateMachine stateMachine,
                           AgentRunCancellationService runCancellation) {
        this.workflows = workflows;
        this.workflowMembers = workflowMembers;
        this.projects = projects;
        this.projectMembers = projectMembers;
        this.access = access;
        this.stateMachine = stateMachine;
        this.runCancellation = runCancellation;
    }

    @Transactional
    public Workflow create(Long actorId, Long projectId, WorkflowDtos.CreateWorkflowRequest request) {
        access.requireMember(projectId, actorId);
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在"));
        if (project.getStatus() != Project.Status.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_ARCHIVED", "归档项目不能创建 Workflow");
        }
        Long parentWorkflowId = validateParent(projectId, request.parentWorkflowId());
        Workflow workflow = workflows.save(new Workflow(projectId, request.title(), request.description(),
                request.intentLevel(), parentWorkflowId, actorId));
        workflowMembers.save(new WorkflowMember(workflow.getId(), actorId, WorkflowMember.Role.OWNER));
        return workflow;
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
        return workflows.save(workflow);
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
}
