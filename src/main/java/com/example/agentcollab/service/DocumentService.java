package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.DocumentVersionRepository;
import com.example.agentcollab.repository.WorkflowRepository;
import com.example.agentcollab.repository.AgentRunRepository;
import com.example.agentcollab.repository.CodeContextVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.Map;

@Service
public class DocumentService {
    private final DocumentVersionRepository documents;
    private final WorkflowRepository workflows;
    private final WorkflowService workflowService;
    private final WorkflowStateMachine stateMachine;
    private final AgentRunRepository agentRuns;
    private final CodeContextVersionRepository contexts;
    private final AuditLogService audit;

    public DocumentService(DocumentVersionRepository documents, WorkflowRepository workflows,
                           WorkflowService workflowService, WorkflowStateMachine stateMachine,
                           AgentRunRepository agentRuns, CodeContextVersionRepository contexts,
                           AuditLogService audit) {
        this.documents = documents;
        this.workflows = workflows;
        this.workflowService = workflowService;
        this.stateMachine = stateMachine;
        this.agentRuns = agentRuns;
        this.contexts = contexts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<DocumentVersion> list(Long actorId, Long workflowId, DocumentType type) {
        workflowService.get(actorId, workflowId);
        return type == null
                ? documents.findByWorkflowIdOrderByCreatedAtDesc(workflowId)
                : documents.findByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(workflowId, type);
    }

    @Transactional
    public DocumentVersion saveDesignEdit(Long actorId, Long workflowId, String content) {
        Workflow workflow = workflowService.requireForUpdate(actorId, workflowId);
        requireStatus(workflow, WorkflowStatus.DESIGN_PROPOSED);
        workflowService.requireEditor(workflow, actorId);
        workflowService.requireActiveProject(workflow);
        DocumentVersion result = saveUserVersion(workflow, DocumentType.DESIGN, DocumentFormat.MARKDOWN, content, actorId);
        AuditSupport.record(audit, actorId, workflow.getProjectId(), "DOCUMENT_EDITED", "DOCUMENT_VERSION", result.getId(), Map.of("type", "DESIGN", "version", result.getVersionNo()));
        return result;
    }

    @Transactional
    public DocumentVersion saveSpecEdit(Long actorId, Long workflowId, String content) {
        Workflow workflow = workflowService.requireForUpdate(actorId, workflowId);
        requireStatus(workflow, WorkflowStatus.SPEC_PROPOSED);
        workflowService.requireEditor(workflow, actorId);
        workflowService.requireActiveProject(workflow);
        DocumentVersion result = saveUserVersion(workflow, DocumentType.SPEC, DocumentFormat.MARKDOWN, content, actorId);
        AuditSupport.record(audit, actorId, workflow.getProjectId(), "DOCUMENT_EDITED", "DOCUMENT_VERSION", result.getId(), Map.of("type", "SPEC", "version", result.getVersionNo()));
        return result;
    }

    @Transactional
    public DocumentVersion confirmDesign(Long actorId, Long workflowId, int versionNo) {
        Workflow workflow = workflowService.requireForUpdate(actorId, workflowId);
        requireStatus(workflow, WorkflowStatus.DESIGN_PROPOSED);
        workflowService.requireCreator(workflow, actorId);
        workflowService.requireActiveProject(workflow);
        DocumentVersion result = confirmLatest(workflow, DocumentType.DESIGN, versionNo, actorId);
        stateMachine.transition(workflow, WorkflowStatus.DESIGN_CONFIRMED);
        workflows.save(workflow);
        AuditSupport.record(audit, actorId, workflow.getProjectId(), "DOCUMENT_CONFIRMED", "DOCUMENT_VERSION", result.getId(), Map.of("type", "DESIGN", "version", versionNo));
        return result;
    }

    @Transactional
    public DocumentVersion confirmSpec(Long actorId, Long workflowId, int versionNo) {
        Workflow workflow = workflowService.requireForUpdate(actorId, workflowId);
        requireStatus(workflow, WorkflowStatus.SPEC_PROPOSED);
        workflowService.requireCreator(workflow, actorId);
        workflowService.requireActiveProject(workflow);
        DocumentVersion confirmed = confirmLatest(workflow, DocumentType.SPEC, versionNo, actorId);
        stateMachine.transition(workflow, WorkflowStatus.SPEC_CONFIRMED);
        workflows.save(workflow);
        AuditSupport.record(audit, actorId, workflow.getProjectId(), "DOCUMENT_CONFIRMED", "DOCUMENT_VERSION", confirmed.getId(), Map.of("type", "SPEC", "version", confirmed.getVersionNo()));
        return confirmed;
    }

    @Transactional
    public DocumentVersion recordGeneratedDesign(Long workflowId, String content, Long agentRunId) {
        Objects.requireNonNull(agentRunId, "agentRunId is required");
        Workflow workflow = workflowService.findForUpdate(workflowId);
        requireStatusIn(workflow, WorkflowStatus.INTENT, WorkflowStatus.DESIGN_PROPOSED);
        workflowService.requireActiveProject(workflow);
        Long contextId = requireCurrentContext(workflow, agentRunId);
        DocumentVersion document = saveAgentVersion(
                workflow, DocumentType.DESIGN, DocumentFormat.MARKDOWN, content, agentRunId, contextId);
        advanceIfNeeded(workflow, WorkflowStatus.DESIGN_PROPOSED);
        return document;
    }

    @Transactional
    public DocumentVersion recordGeneratedSpec(Long workflowId, String content, Long agentRunId) {
        Objects.requireNonNull(agentRunId, "agentRunId is required");
        Workflow workflow = workflowService.findForUpdate(workflowId);
        requireStatusIn(workflow, WorkflowStatus.DESIGN_CONFIRMED, WorkflowStatus.SPEC_PROPOSED);
        workflowService.requireActiveProject(workflow);
        requireLatestConfirmed(workflowId, DocumentType.DESIGN);
        Long contextId = requireCurrentContext(workflow, agentRunId);
        DocumentVersion document = saveAgentVersion(
                workflow, DocumentType.SPEC, DocumentFormat.MARKDOWN, content, agentRunId, contextId);
        advanceIfNeeded(workflow, WorkflowStatus.SPEC_PROPOSED);
        return document;
    }

    @Transactional
    public DocumentVersion recordGeneratedBuildPlan(Long workflowId, String content, Long agentRunId) {
        Objects.requireNonNull(agentRunId, "agentRunId is required");
        Workflow workflow = workflowService.findForUpdate(workflowId);
        WorkflowStatus initialStatus = workflow.getIntentLevel() == IntentLevel.CHANGE
                ? WorkflowStatus.INTENT : WorkflowStatus.SPEC_CONFIRMED;
        requireStatusIn(workflow, initialStatus, WorkflowStatus.BUILD_PLAN_PROPOSED);
        workflowService.requireActiveProject(workflow);
        Long contextId = requireCurrentContext(workflow, agentRunId);
        DocumentVersion document = saveAgentVersion(
                workflow, DocumentType.BUILD_PLAN, DocumentFormat.JSON, content, agentRunId, contextId);
        advanceIfNeeded(workflow, WorkflowStatus.BUILD_PLAN_PROPOSED);
        return document;
    }

    private void advanceIfNeeded(Workflow workflow, WorkflowStatus target) {
        if (workflow.getStatus() == target) return;
        stateMachine.transition(workflow, target);
        workflows.save(workflow);
    }

    private DocumentVersion saveUserVersion(Workflow workflow, DocumentType type, DocumentFormat format,
                                            String content, Long actorId) {
        if (content == null || content.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_DOCUMENT", "文档内容不能为空");
        }
        Long contextId = documents.findTopByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(workflow.getId(), type)
                .map(DocumentVersion::getCodeContextVersionId).orElse(null);
        return documents.save(DocumentVersion.byUser(workflow.getId(), type, nextVersion(workflow.getId(), type),
                content, format, actorId, contextId));
    }

    private DocumentVersion saveAgentVersion(Workflow workflow, DocumentType type, DocumentFormat format,
                                             String content, Long agentRunId, Long codeContextVersionId) {
        if (content == null || content.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_AGENT_DOCUMENT", "Agent 文档内容为空");
        }
        return documents.save(DocumentVersion.byAgent(workflow.getId(), type, nextVersion(workflow.getId(), type),
                content, format, agentRunId, codeContextVersionId));
    }

    private Long requireCurrentContext(Workflow workflow, Long agentRunId) {
        AgentRun run = agentRuns.findById(agentRunId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "AGENT_RUN_NOT_FOUND", "Agent 文档缺少运行记录"));
        if (!workflow.getId().equals(run.getWorkflowId()) || run.getCodeContextVersionId() == null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "CODE_CONTEXT_MISMATCH", "AgentRun 未绑定当前 Workflow 的 Code Context");
        }
        CodeContextVersion context = contexts.findByIdForUpdate(run.getCodeContextVersionId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "CODE_CONTEXT_MISSING", "AgentRun 绑定的 Code Context 不存在"));
        if (!workflow.getProjectId().equals(context.getProjectId())
                || context.getStatus() != CodeContextStatus.CURRENT) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "CODE_CONTEXT_STALE", "AgentRun 绑定的 Code Context 已过期");
        }
        return context.getId();
    }

    private DocumentVersion confirmLatest(Workflow workflow, DocumentType type, int versionNo, Long actorId) {
        DocumentVersion latest = latest(workflow.getId(), type);
        if (latest.getVersionNo() != versionNo) {
            throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_VERSION_STALE", "只能确认当前最新文档版本");
        }
        latest.confirm(actorId);
        return documents.save(latest);
    }

    private void requireLatestConfirmed(Long workflowId, DocumentType type) {
        DocumentVersion latest = latest(workflowId, type);
        if (!latest.isConfirmed()) {
            throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_NOT_CONFIRMED", "当前文档版本尚未确认");
        }
    }

    private DocumentVersion latest(Long workflowId, DocumentType type) {
        return documents.findTopByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(workflowId, type)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "DOCUMENT_NOT_FOUND", "当前阶段缺少文档"));
    }

    private int nextVersion(Long workflowId, DocumentType type) {
        return documents.findTopByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(workflowId, type)
                .map(value -> value.getVersionNo() + 1).orElse(1);
    }

    private void requireStatus(Workflow workflow, WorkflowStatus expected) {
        if (workflow.getStatus() != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "WORKFLOW_STATE_CONFLICT",
                    "当前 Workflow 状态不允许此操作");
        }
    }

    private void requireStatusIn(Workflow workflow, WorkflowStatus... allowed) {
        if (!List.of(allowed).contains(workflow.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "WORKFLOW_STATE_CONFLICT",
                    "当前 Workflow 状态不允许此操作");
        }
    }
}
