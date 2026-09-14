package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.dto.PlanDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.DocumentVersionRepository;
import com.example.agentcollab.repository.ProjectMemberRepository;
import com.example.agentcollab.repository.UserRepository;
import com.example.agentcollab.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

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
    private final ObjectMapper json;
    private final TaskGranularityValidator granularity;

    public PlanService(DocumentVersionRepository documents, WorkflowRepository workflows,
                       ProjectMemberRepository members, UserRepository users,
                       WorkflowService workflowService, ProjectAccessService access,
                       WorkflowStateMachine stateMachine, BuildPlanValidator validator,
                       AuditLogService audit, ObjectMapper json, TaskGranularityValidator granularity) {
        this.documents = documents;
        this.workflows = workflows;
        this.members = members;
        this.users = users;
        this.workflowService = workflowService;
        this.access = access;
        this.stateMachine = stateMachine;
        this.validator = validator;
        this.audit = audit;
        this.json = json;
        this.granularity = granularity;
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
        return approve(actorId, workflowId, versionNo, null);
    }

    @Transactional
    public DocumentVersion approve(Long actorId, Long workflowId, int versionNo, String reason) {
        Workflow workflow = requireProposedPlan(actorId, workflowId);
        DocumentVersion latest = latest(workflowId);
        if (latest.getVersionNo() != versionNo) {
            throw conflict("DOCUMENT_VERSION_STALE", "只能批准当前最新 Build Plan 版本");
        }
        JsonNode plan = validate(latest.getContent(), workflow.getIntentLevel());
        validateAssignees(workflow, plan, true);
        List<TaskGranularityValidator.Warning> warnings = granularity.analyze(plan, workflow.getIntentLevel());
        if (!warnings.isEmpty() && (reason == null || reason.isBlank())) {
            throw conflict("TASK_GRANULARITY_REASON_REQUIRED", "当前 Build Plan 仍有任务粒度警告，批准前必须填写保留拆分理由");
        }
        latest.confirm(actorId);
        stateMachine.transition(workflow, WorkflowStatus.PLAN_APPROVED);
        workflows.save(workflow);
        DocumentVersion result = documents.save(latest);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("version", versionNo);
        if (!warnings.isEmpty()) {
            details.put("granularityWarnings", warnings.stream().map(TaskGranularityValidator.Warning::message).toList());
            details.put("overrideReason", reason.trim());
            details.put("overrideAction", "APPROVE_WITH_SPLIT");
        }
        AuditSupport.record(audit, actorId, workflow.getProjectId(), "BUILD_PLAN_APPROVED", "DOCUMENT_VERSION", result.getId(), details);
        return result;
    }

    @Transactional(readOnly = true)
    public PlanDtos.GranularityResponse granularity(Long actorId, Long workflowId) {
        Workflow workflow = workflowService.get(actorId, workflowId);
        DocumentVersion latest = latest(workflowId);
        JsonNode plan = validate(latest.getContent(), workflow.getIntentLevel());
        List<TaskGranularityValidator.Warning> warnings = granularity.analyze(plan, workflow.getIntentLevel());
        return new PlanDtos.GranularityResponse(latest.getVersionNo(), warnings.stream()
                .map(w -> new PlanDtos.GranularityWarningResponse(w.code(), w.message(), w.taskKeys(), w.suggestedAction())).toList());
    }

    @Transactional
    public DocumentVersion applyGranularityDecision(Long actorId, Long workflowId, PlanDtos.TaskMergeRequest request) {
        Workflow workflow = requireProposedPlan(actorId, workflowId);
        DocumentVersion latest = latest(workflowId);
        JsonNode root = validate(latest.getContent(), workflow.getIntentLevel());
        List<String> keys = request.taskKeys() == null ? List.of() : request.taskKeys().stream().distinct().toList();
        if ("KEEP_SPLIT".equalsIgnoreCase(request.operation())) {
            if (request.reason() == null || request.reason().isBlank()) {
                throw conflict("TASK_GRANULARITY_REASON_REQUIRED", "保留任务拆分必须填写理由");
            }
            AuditSupport.record(audit, actorId, workflow.getProjectId(), "TASK_SPLIT_RETAINED",
                    "DOCUMENT_VERSION", latest.getId(),
                    Map.of("taskKeys", keys, "reason", request.reason().trim()));
            return latest;
        }
        if (!"MERGE".equalsIgnoreCase(request.operation()) || keys.size() < 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TASK_MERGE", "合并操作至少需要选择两个 Task");
        }
        ObjectNode mutable = (ObjectNode) root.deepCopy();
        ArrayNode taskArray = (ArrayNode) mutable.withArray("tasks");
        ObjectNode merged = null;
        Set<String> selected = new LinkedHashSet<>(keys);
        List<JsonNode> removed = new ArrayList<>();
        for (JsonNode task : taskArray) {
            if (!selected.contains(task.path("taskKey").asText())) continue;
            if (merged == null) merged = (ObjectNode) task.deepCopy();
            else removed.add(task);
        }
        if (merged == null || removed.size() != keys.size() - 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TASK_MERGE", "选择的 Task 不存在");
        }
        String keep = merged.path("taskKey").asText();
        mergeArrayValues(merged, removed, "scope");
        mergeArrayValues(merged, removed, "nonGoals");
        mergeArrayValues(merged, removed, "acceptanceCriteria");
        mergeArrayValues(merged, removed, "verificationCommands");
        int effort = merged.path("effortPoints").asInt();
        for (JsonNode task : removed) effort += task.path("effortPoints").asInt();
        if (effort > 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TASK_MERGE",
                    "合并后的 Task 工作量不能超过 8 点，请先调整估算或保留拆分");
        }
        merged.put("effortPoints", effort);
        LinkedHashSet<String> dependencies = new LinkedHashSet<>();
        for (JsonNode task : taskArray) {
            if (!selected.contains(task.path("taskKey").asText())) continue;
            for (JsonNode value : task.path("dependencies")) {
                String dependency = value.asText();
                if (!selected.contains(dependency) && !dependency.equals(keep)) dependencies.add(dependency);
            }
        }
        merged.set("dependencies", json.valueToTree(dependencies));
        for (int index = 0; index < taskArray.size(); index++) {
            if (taskArray.get(index).path("taskKey").asText().equals(keep)) taskArray.set(index, merged);
        }
        for (int index = taskArray.size() - 1; index >= 0; index--) {
            String taskKey = taskArray.get(index).path("taskKey").asText();
            if (selected.contains(taskKey) && !taskKey.equals(keep)) taskArray.remove(index);
        }
        for (JsonNode task : taskArray) {
            LinkedHashSet<String> rewrittenDependencies = new LinkedHashSet<>();
            for (JsonNode dependency : task.path("dependencies")) {
                String value = selected.contains(dependency.asText()) ? keep : dependency.asText();
                if (!value.equals(task.path("taskKey").asText())) rewrittenDependencies.add(value);
            }
            ArrayNode rewritten = json.createArrayNode();
            rewrittenDependencies.forEach(rewritten::add);
            ((ObjectNode) task).set("dependencies", rewritten);
        }
        ArrayNode assigns = (ArrayNode) mutable.withArray("assignments");
        for (int index = assigns.size() - 1; index >= 0; index--) {
            String taskKey = assigns.get(index).path("taskKey").asText();
            if (selected.contains(taskKey) && !taskKey.equals(keep)) assigns.remove(index);
        }
        updateStaffingRecommendation(mutable, assigns);
        String content = mutable.toString();
        JsonNode validated = validate(content, workflow.getIntentLevel());
        validateAssignees(workflow, validated, true);
        DocumentVersion result = documents.save(DocumentVersion.byUser(workflowId, DocumentType.BUILD_PLAN, latest.getVersionNo()+1, content, DocumentFormat.JSON, actorId, latest.getCodeContextVersionId()));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("taskKeys", keys);
        details.put("retainedTaskKey", keep);
        if (request.reason() != null && !request.reason().isBlank()) details.put("reason", request.reason().trim());
        AuditSupport.record(audit, actorId, workflow.getProjectId(), "TASKS_MERGED", "DOCUMENT_VERSION", result.getId(), details);
        return result;
    }

    private void mergeArrayValues(ObjectNode target, List<JsonNode> removed, String field) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        target.path(field).forEach(value -> values.add(value.asText()));
        for (JsonNode node : removed) node.path(field).forEach(value -> values.add(value.asText()));
        ArrayNode out = json.createArrayNode();
        values.forEach(out::add);
        target.set(field, out);
    }

    private void updateStaffingRecommendation(ObjectNode plan, ArrayNode assignments) {
        Set<Long> assigneeIds = new LinkedHashSet<>();
        assignments.forEach(assignment -> assigneeIds.add(assignment.path("userId").asLong()));
        ObjectNode recommendation = (ObjectNode) plan.path("staffingRecommendation");
        int teamSize = assigneeIds.size();
        recommendation.put("recommendedTeamSize", teamSize);
        if (teamSize == 1) recommendation.put("mode", "SINGLE_OWNER");
        else if (teamSize == 2) recommendation.put("mode", "PAIR");
        else if (teamSize > 2) recommendation.put("mode", "TEAM");
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
