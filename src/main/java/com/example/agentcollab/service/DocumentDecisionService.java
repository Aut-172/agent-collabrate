package com.example.agentcollab.service;

import com.example.agentcollab.domain.DocumentDecision;
import com.example.agentcollab.domain.DocumentDecisionStatus;
import com.example.agentcollab.domain.DocumentType;
import com.example.agentcollab.domain.DocumentVersion;
import com.example.agentcollab.domain.Workflow;
import com.example.agentcollab.dto.DocumentDecisionDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.DocumentDecisionRepository;
import com.example.agentcollab.repository.DocumentVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocumentDecisionService {
    private final DocumentDecisionRepository decisions;
    private final DocumentVersionRepository documents;
    private final WorkflowService workflows;
    private final DocumentDecisionParser parser;
    private final ObjectMapper json;
    private final AuditLogService audit;

    public DocumentDecisionService(DocumentDecisionRepository decisions, DocumentVersionRepository documents,
                                   WorkflowService workflows, DocumentDecisionParser parser,
                                   ObjectMapper json, AuditLogService audit) {
        this.decisions = decisions;
        this.documents = documents;
        this.workflows = workflows;
        this.parser = parser;
        this.json = json;
        this.audit = audit;
    }

    @Transactional
    public void sync(DocumentVersion document) {
        if (document.getDocumentType() != DocumentType.DESIGN
                && document.getDocumentType() != DocumentType.SPEC) return;
        List<DocumentDecision> parsed = parser.parse(document.getContent()).stream()
                .map(value -> new DocumentDecision(document.getWorkflowId(), document.getId(),
                        document.getDocumentType(), value.decisionKey(), value.question(),
                        json.valueToTree(value.options()), value.recommendedOption(), value.unresolvedImpact()))
                .toList();
        decisions.saveAll(parsed);
    }

    @Transactional(readOnly = true)
    public List<DocumentDecisionDtos.DecisionResponse> listCurrent(Long actorId, Long workflowId) {
        workflows.get(actorId, workflowId);
        List<Long> currentVersionIds = new ArrayList<>();
        documents.findTopByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(workflowId, DocumentType.DESIGN)
                .map(DocumentVersion::getId).ifPresent(currentVersionIds::add);
        documents.findTopByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(workflowId, DocumentType.SPEC)
                .map(DocumentVersion::getId).ifPresent(currentVersionIds::add);
        if (currentVersionIds.isEmpty()) return List.of();
        return decisions.findByDocumentVersionIdInOrderByDocumentVersionIdAscDecisionKeyAsc(currentVersionIds)
                .stream().map(DocumentDecisionDtos.DecisionResponse::from).toList();
    }

    @Transactional
    public DocumentDecisionDtos.DecisionResponse resolve(Long actorId, Long workflowId, Long decisionId,
                                                          String selectedOption) {
        Workflow workflow = workflows.get(actorId, workflowId);
        workflows.requireEditor(workflow, actorId);
        workflows.requireActiveProject(workflow);
        DocumentDecision decision = decisions.findByIdForUpdate(decisionId)
                .filter(value -> value.getWorkflowId().equals(workflowId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "DOCUMENT_DECISION_NOT_FOUND", "待确认决策不存在"));
        DocumentVersion latest = documents.findTopByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(
                        workflowId, decision.getDocumentType())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "DOCUMENT_VERSION_STALE", "待确认决策所属文档版本已过期"));
        if (!latest.getId().equals(decision.getDocumentVersionId())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "DOCUMENT_DECISION_STALE", "只能处理当前文档版本的待确认决策");
        }
        if (decision.getStatus() != DocumentDecisionStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "DOCUMENT_DECISION_RESOLVED", "当前决策已经完成确认，不能重复修改");
        }
        boolean knownOption = false;
        for (var option : decision.getOptionsJson()) {
            if (selectedOption.equals(option.path("key").asText())) {
                knownOption = true;
                break;
            }
        }
        if (!knownOption) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "DOCUMENT_DECISION_OPTION_INVALID", "选择项不属于当前决策");
        }
        decision.resolve(selectedOption, actorId);
        DocumentDecision saved = decisions.save(decision);
        AuditSupport.record(audit, actorId, workflow.getProjectId(), "DOCUMENT_DECISION_RESOLVED",
                "DOCUMENT_DECISION", saved.getId(), Map.of(
                        "documentVersionId", saved.getDocumentVersionId(),
                        "decisionKey", saved.getDecisionKey(),
                        "selectedOption", selectedOption));
        return DocumentDecisionDtos.DecisionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public void requireResolved(DocumentVersion document) {
        List<DocumentDecision> open = decisions.findByDocumentVersionIdAndStatusOrderByDecisionKey(
                document.getId(), DocumentDecisionStatus.OPEN);
        if (open.isEmpty()) return;
        throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_DECISIONS_OPEN",
                "请先完成当前文档的待确认决策",
                Map.of("decisionKeys", open.stream().map(DocumentDecision::getDecisionKey).toList()));
    }
}
