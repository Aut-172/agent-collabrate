package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.DocumentVersionRepository;
import com.example.agentcollab.repository.WorkflowRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

@Service
public class DocumentService {
    private final DocumentVersionRepository documents;
    private final WorkflowRepository workflows;
    private final WorkflowService workflowService;
    private final WorkflowStateMachine stateMachine;

    public DocumentService(DocumentVersionRepository documents, WorkflowRepository workflows,
                           WorkflowService workflowService, WorkflowStateMachine stateMachine) {
        this.documents = documents;
        this.workflows = workflows;
        this.workflowService = workflowService;
        this.stateMachine = stateMachine;
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
        return saveUserVersion(workflow, DocumentType.DESIGN, DocumentFormat.MARKDOWN, content, actorId);
    }

    @Transactional
    public DocumentVersion saveSpecEdit(Long actorId, Long workflowId, String content) {
        Workflow workflow = workflowService.requireForUpdate(actorId, workflowId);
        requireStatus(workflow, WorkflowStatus.SPEC_PROPOSED);
        workflowService.requireEditor(workflow, actorId);
        workflowService.requireActiveProject(workflow);
        return saveUserVersion(workflow, DocumentType.SPEC, DocumentFormat.MARKDOWN, content, actorId);
    }

    @Transactional
    public DocumentVersion confirmDesign(Long actorId, Long workflowId, int versionNo) {
        Workflow workflow = workflowService.requireForUpdate(actorId, workflowId);
        requireStatus(workflow, WorkflowStatus.DESIGN_PROPOSED);
        workflowService.requireCreator(workflow, actorId);
        workflowService.requireActiveProject(workflow);
        return confirmLatest(workflow, DocumentType.DESIGN, versionNo, actorId);
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
        return confirmed;
    }

    @Transactional
    public DocumentVersion recordGeneratedDesign(Long workflowId, String content, Long agentRunId) {
        Objects.requireNonNull(agentRunId, "agentRunId is required");
        Workflow workflow = workflowService.findForUpdate(workflowId);
        requireStatus(workflow, WorkflowStatus.INTENT);
        workflowService.requireActiveProject(workflow);
        DocumentVersion document = saveAgentVersion(
                workflow, DocumentType.DESIGN, DocumentFormat.MARKDOWN, content, agentRunId);
        stateMachine.transition(workflow, WorkflowStatus.DESIGN_PROPOSED);
        workflows.save(workflow);
        return document;
    }

    @Transactional
    public DocumentVersion recordGeneratedSpec(Long workflowId, String content, Long agentRunId) {
        Objects.requireNonNull(agentRunId, "agentRunId is required");
        Workflow workflow = workflowService.findForUpdate(workflowId);
        requireStatus(workflow, WorkflowStatus.DESIGN_PROPOSED);
        workflowService.requireActiveProject(workflow);
        requireLatestConfirmed(workflowId, DocumentType.DESIGN);
        DocumentVersion document = saveAgentVersion(
                workflow, DocumentType.SPEC, DocumentFormat.MARKDOWN, content, agentRunId);
        stateMachine.transition(workflow, WorkflowStatus.SPEC_PROPOSED);
        workflows.save(workflow);
        return document;
    }

    private DocumentVersion saveUserVersion(Workflow workflow, DocumentType type, DocumentFormat format,
                                            String content, Long actorId) {
        if (content == null || content.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_DOCUMENT", "文档内容不能为空");
        }
        return documents.save(DocumentVersion.byUser(workflow.getId(), type, nextVersion(workflow.getId(), type),
                content, format, actorId));
    }

    private DocumentVersion saveAgentVersion(Workflow workflow, DocumentType type, DocumentFormat format,
                                             String content, Long agentRunId) {
        if (content == null || content.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_AGENT_DOCUMENT", "Agent 文档内容为空");
        }
        return documents.save(DocumentVersion.byAgent(workflow.getId(), type, nextVersion(workflow.getId(), type),
                content, format, agentRunId));
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
}
