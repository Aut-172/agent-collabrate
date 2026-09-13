package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.DocumentVersionRepository;
import com.example.agentcollab.repository.ProjectMemberRepository;
import com.example.agentcollab.repository.UserRepository;
import com.example.agentcollab.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service
public class PlanService {
    private final DocumentVersionRepository documents;
    private final WorkflowRepository workflows;
    private final ProjectMemberRepository members;
    private final UserRepository users;
    private final WorkflowService workflowService;
    private final ProjectAccessService access;
    private final WorkflowStateMachine stateMachine;
    private final BuildPlanValidator validator;
    private final AuditLogService audit;

    public PlanService(DocumentVersionRepository documents, WorkflowRepository workflows,
                       ProjectMemberRepository members, UserRepository users,
                       WorkflowService workflowService, ProjectAccessService access,
                       WorkflowStateMachine stateMachine, BuildPlanValidator validator,
                       AuditLogService audit) {
        this.documents = documents;
        this.workflows = workflows;
        this.members = members;
        this.users = users;
        this.workflowService = workflowService;
        this.access = access;
        this.stateMachine = stateMachine;
        this.validator = validator;
        this.audit = audit;
    }

    @Transactional
    public DocumentVersion saveDraft(Long actorId, Long workflowId, String content) {
        Workflow workflow = requireProposedPlan(actorId, workflowId);
        JsonNode plan = validate(content, workflow.getIntentLevel());
        validateAssignees(workflow, plan, true);
        DocumentVersion previous = latest(workflowId);
        int version = previous.getVersionNo() + 1;
        DocumentVersion result = documents.save(DocumentVersion.byUser(
                workflowId, DocumentType.BUILD_PLAN, version, content, DocumentFormat.JSON, actorId,
                previous.getCodeContextVersionId()));
        AuditSupport.record(audit, actorId, workflow.getProjectId(), "BUILD_PLAN_EDITED", "DOCUMENT_VERSION", result.getId(), Map.of("version", version));
        return result;
    }

    @Transactional
    public DocumentVersion approve(Long actorId, Long workflowId, int versionNo) {
        Workflow workflow = requireProposedPlan(actorId, workflowId);
        DocumentVersion latest = latest(workflowId);
        if (latest.getVersionNo() != versionNo) {
            throw conflict("DOCUMENT_VERSION_STALE", "只能批准当前最新 Build Plan 版本");
        }
        JsonNode plan = validate(latest.getContent(), workflow.getIntentLevel());
        validateAssignees(workflow, plan, true);
        latest.confirm(actorId);
        stateMachine.transition(workflow, WorkflowStatus.PLAN_APPROVED);
        workflows.save(workflow);
        DocumentVersion result = documents.save(latest);
        AuditSupport.record(audit, actorId, workflow.getProjectId(), "BUILD_PLAN_APPROVED", "DOCUMENT_VERSION", result.getId(), Map.of("version", versionNo));
        return result;
    }

    public JsonNode validateForWorkflow(Workflow workflow, String content) {
        JsonNode plan = validate(content, workflow.getIntentLevel());
        validateAssignees(workflow, plan, false);
        return plan;
    }

    private Workflow requireProposedPlan(Long actorId, Long workflowId) {
        Workflow workflow = workflowService.requireForUpdate(actorId, workflowId);
        access.requireLeader(workflow.getProjectId(), actorId);
        workflowService.requireActiveProject(workflow);
        if (workflow.getStatus() != WorkflowStatus.BUILD_PLAN_PROPOSED) {
            throw conflict("WORKFLOW_STATE_CONFLICT", "当前 Workflow 状态不允许修改或批准 Build Plan");
        }
        return workflow;
    }

    private JsonNode validate(String content, IntentLevel level) {
        try {
            return validator.validate(content, level);
        } catch (BuildPlanValidationException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BUILD_PLAN", ex.getMessage());
        }
    }

    private void validateAssignees(Workflow workflow, JsonNode plan, boolean requireCurrentPlanSnapshot) {
        if (workflow.getIntentLevel() == IntentLevel.ARCHITECTURE) return;
        for (JsonNode assignment : plan.path("assignments")) {
            Long userId = assignment.path("userId").asLong();
            if (workflow.getCompletionMode() == WorkflowCompletionMode.CI_BOOTSTRAP
                    && !workflow.getCreatedBy().equals(userId)) {
                throw conflict("CI_BOOTSTRAP_LEADER_ASSIGNEE_REQUIRED",
                        "工程与 CI 初始化任务必须分配给创建该 Workflow 的项目 Leader");
            }
            ProjectMember member = members.findByProjectIdAndUserIdForUpdate(workflow.getProjectId(), userId)
                    .filter(value -> value.getStatus() == ProjectMember.Status.ACTIVE)
                    .orElseThrow(() -> conflict("ASSIGNEE_NOT_ACTIVE", "负责人不是当前项目的有效成员"));
            if (!users.findById(userId).map(User::isEnabled).orElse(false)
                    || !member.isProfileCompleted()
                    || member.getCapabilityProfile() == null
                    || member.getWeeklyCapacityPoints() == null
                    || member.getAvailability() == null) {
                throw conflict("ASSIGNEE_NOT_ASSIGNABLE", "负责人尚未完成可分配画像");
            }
            if (workflow.getCompletionMode() == WorkflowCompletionMode.CI_BOOTSTRAP
                    && member.getProjectRole() != ProjectMember.Role.LEADER) {
                throw conflict("CI_BOOTSTRAP_LEADER_ASSIGNEE_REQUIRED",
                        "工程与 CI 初始化任务必须分配给创建该 Workflow 的项目 Leader");
            }
            if (requireCurrentPlanSnapshot
                    && !member.getProjectRole().name().equals(assignment.path("projectRole").asText())) {
                throw conflict("ASSIGNEE_ROLE_STALE", "负责人项目角色已变化");
            }
            if (requireCurrentPlanSnapshot
                    && member.getProfileVersion() != assignment.path("profileVersion").asInt()) {
                throw conflict("ASSIGNEE_PROFILE_STALE", "负责人画像版本已变化，请更新或重新生成计划");
            }
            // Workload and weekly capacity are planning signals, not hard limits.
            // Task creation records a fresh workload snapshot, so a plan remains
            // approvable when capacity changes between generation and approval.
        }
    }

    private DocumentVersion latest(Long workflowId) {
        return documents.findTopByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(
                        workflowId, DocumentType.BUILD_PLAN)
                .orElseThrow(() -> conflict("DOCUMENT_NOT_FOUND", "当前 Workflow 缺少 Build Plan"));
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
