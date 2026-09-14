package com.example.agentcollab.service;

import com.example.agentcollab.client.AgentGenerationRequest;
import com.example.agentcollab.client.AgentProviderException;
import com.example.agentcollab.domain.*;
import com.example.agentcollab.repository.DocumentVersionRepository;
import com.example.agentcollab.repository.AgentRunRepository;
import com.example.agentcollab.repository.ProjectMemberRepository;
import com.example.agentcollab.repository.UserRepository;
import com.example.agentcollab.repository.WorkflowRepository;
import com.example.agentcollab.repository.TaskRepository;
import com.example.agentcollab.repository.RepoInventoryFileRepository;
import com.example.agentcollab.repository.RepoInventoryVersionRepository;
import com.example.agentcollab.repository.CodeContextFileRepository;
import com.example.agentcollab.repository.CodeContextVersionRepository;
import com.example.agentcollab.repository.DocumentDecisionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class AgentRequestFactory {
    private final WorkflowRepository workflows;
    private final AgentRunRepository runs;
    private final DocumentVersionRepository documents;
    private final DocumentDecisionRepository decisions;
    private final ProjectMemberRepository members;
    private final UserRepository users;
    private final TaskRepository tasks;
    private final RepoInventoryVersionRepository inventories;
    private final RepoInventoryFileRepository inventoryFiles;
    private final CodeContextVersionRepository contexts;
    private final CodeContextFileRepository contextFiles;
    private final int maxInventoryFiles;

    public AgentRequestFactory(WorkflowRepository workflows, AgentRunRepository runs,
                               DocumentVersionRepository documents,
                               DocumentDecisionRepository decisions,
                               ProjectMemberRepository members, UserRepository users,
                               TaskRepository tasks, RepoInventoryVersionRepository inventories,
                               RepoInventoryFileRepository inventoryFiles, CodeContextVersionRepository contexts,
                               CodeContextFileRepository contextFiles,
                               @Value("${app.code-context.max-plan-inventory-files:1000}") int maxInventoryFiles) {
        this.workflows = workflows;
        this.runs = runs;
        this.documents = documents;
        this.decisions = decisions;
        this.members = members;
        this.users = users;
        this.tasks = tasks;
        this.inventories = inventories;
        this.inventoryFiles = inventoryFiles;
        this.contexts = contexts;
        this.contextFiles = contextFiles;
        this.maxInventoryFiles = maxInventoryFiles;
    }

    @Transactional(readOnly = true)
    public AgentGenerationRequest create(Long runId) {
        AgentRun run = runs.findById(runId).orElseThrow(() -> new AgentProviderException(
                "AGENT_RUN_NOT_FOUND", "AgentRun 不存在", false));
        Workflow workflow = workflows.findById(run.getWorkflowId())
                .orElseThrow(() -> new AgentProviderException(
                        "WORKFLOW_NOT_FOUND", "Workflow 不存在", false));
        List<AgentGenerationRequest.MemberContext> assignableMembers =
                workflow.getIntentLevel() == IntentLevel.ARCHITECTURE
                        ? List.of() : assignableMembers(workflow);
        if (run.getRunType() == AgentRunType.GENERATE_BUILD_PLAN
                && workflow.getIntentLevel() != IntentLevel.ARCHITECTURE
                && assignableMembers.isEmpty()) {
            throw new AgentProviderException(
                    "NO_ASSIGNABLE_MEMBERS", "项目中没有已完成画像且可投入的成员", false);
        }
        String design = run.getRunType() == AgentRunType.GENERATE_DESIGN
                ? null : latestConfirmed(workflow.getId(), DocumentType.DESIGN);
        String spec = run.getRunType() == AgentRunType.GENERATE_BUILD_PLAN
                ? latestConfirmed(workflow.getId(), DocumentType.SPEC) : null;
        List<AgentGenerationRequest.DecisionContext> resolvedDecisions = resolvedDecisions(
                workflow.getId(), run.getRunType());
        return new AgentGenerationRequest(workflow.getId(), run.getRunType(), workflow.getIntentLevel(),
                workflow.getTitle(), workflow.getDescription(), design, spec, assignableMembers,
                repoInventory(run), codeContext(run), resolvedDecisions);
    }

    private AgentGenerationRequest.RepoInventoryInput repoInventory(AgentRun run) {
        if (run.getRunType() != AgentRunType.GENERATE_CODE_CONTEXT_PLAN) return null;
        RepoInventoryVersion inventory = inventories.findById(run.getInventoryVersionId())
                .filter(value -> value.getStatus() == RepoInventoryStatus.CURRENT)
                .orElseThrow(() -> new AgentProviderException(
                        "REPO_INVENTORY_REFRESH_REQUIRED", "当前项目缺少可用 Repo Inventory，请先完成刷新", false));
        var files = inventoryFiles.findByInventoryVersionIdOrderByPath(inventory.getId()).stream()
                .limit(maxInventoryFiles)
                .map(file -> new AgentGenerationRequest.InventoryFileContext(file.getPath(),
                        file.getFileType().name(), file.getSizeBytes(), file.getContentHash(), file.getIndexedSummary()))
                .toList();
        return new AgentGenerationRequest.RepoInventoryInput(inventory.getId(), inventory.getCommitSha(),
                inventory.getRepositoryProfile(), inventory.getTreeSummary(), files);
    }

    private AgentGenerationRequest.CodeContextInput codeContext(AgentRun run) {
        if (run.getRunType() == AgentRunType.GENERATE_CODE_CONTEXT_PLAN) return null;
        CodeContextVersion context = contexts.findById(run.getCodeContextVersionId())
                .filter(value -> value.getStatus() == CodeContextStatus.CURRENT)
                .orElseThrow(() -> new AgentProviderException(
                        "CODE_CONTEXT_STALE", "AgentRun 绑定的 Code Context 已过期", false));
        if (!context.getContextPlanId().equals(run.getContextPlanId())
                || !context.getInventoryVersionId().equals(run.getInventoryVersionId())) {
            throw new AgentProviderException("CODE_CONTEXT_MISMATCH", "AgentRun 的 Code Context 引用不一致", false);
        }
        RepoInventoryVersion inventory = inventories.findById(context.getInventoryVersionId())
                .filter(value -> value.getStatus() == RepoInventoryStatus.CURRENT)
                .orElseThrow(() -> new AgentProviderException(
                        "CODE_CONTEXT_STALE", "Code Context 绑定的 Repo Inventory 已过期", false));
        if (!context.getBaseCommitSha().equalsIgnoreCase(inventory.getCommitSha())) {
            throw new AgentProviderException("CODE_CONTEXT_MISMATCH", "Code Context 的 Commit 引用不一致", false);
        }
        var files = contextFiles.findByContextVersionIdOrderByPath(context.getId()).stream()
                .map(file -> new AgentGenerationRequest.EvidenceFileContext(file.getPath(),
                        file.getEvidenceType().name(), file.getContentHash(), file.getSummary(),
                        file.getImportantSymbols(), file.getExcerpt()))
                .toList();
        return new AgentGenerationRequest.CodeContextInput(context.getId(), context.getContextPlanId(),
                context.getInventoryVersionId(), context.getBaseCommitSha(), context.getRepositoryProfile(),
                context.getEvidenceJson(), files);
    }

    private List<AgentGenerationRequest.MemberContext> assignableMembers(Workflow workflow) {
        return members.findByProjectIdAndStatus(workflow.getProjectId(), ProjectMember.Status.ACTIVE).stream()
                .filter(member -> workflow.getCompletionMode() != WorkflowCompletionMode.CI_BOOTSTRAP
                        || member.getUserId().equals(workflow.getCreatedBy()))
                .filter(ProjectMember::isProfileCompleted)
                .filter(member -> member.getCapabilityProfile() != null)
                .filter(member -> member.getWeeklyCapacityPoints() != null)
                .filter(member -> member.getAvailability() != null && !member.getAvailability().isBlank())
                .filter(member -> users.findById(member.getUserId()).map(User::isEnabled).orElse(false))
                .map(member -> new AgentGenerationRequest.MemberContext(
                        member.getUserId(), member.getProjectRole(), member.getProfileVersion(),
                        member.getCapabilityProfile().deepCopy(),
                        tasks.sumOpenEffortPoints(workflow.getProjectId(), member.getUserId()),
                        member.getWeeklyCapacityPoints(),
                        member.getAvailability()))
                .toList();
    }

    private String latestConfirmed(Long workflowId, DocumentType type) {
        return latestConfirmedDocument(workflowId, type)
                .map(DocumentVersion::getContent).orElse(null);
    }

    private List<AgentGenerationRequest.DecisionContext> resolvedDecisions(Long workflowId, AgentRunType runType) {
        List<DocumentType> upstream = switch (runType) {
            case GENERATE_SPEC -> List.of(DocumentType.DESIGN);
            case GENERATE_BUILD_PLAN -> List.of(DocumentType.DESIGN, DocumentType.SPEC);
            default -> List.of();
        };
        return upstream.stream()
                .map(type -> latestConfirmedDocument(workflowId, type).orElse(null))
                .filter(java.util.Objects::nonNull)
                .flatMap(document -> decisions.findByDocumentVersionIdAndStatusOrderByDecisionKey(
                                document.getId(), DocumentDecisionStatus.RESOLVED).stream()
                        .map(decision -> new AgentGenerationRequest.DecisionContext(
                                document.getId(), document.getDocumentType().name(), decision.getDecisionKey(),
                                decision.getQuestion(), decision.getSelectedOption(), selectedOptionLabel(decision))))
                .toList();
    }

    private java.util.Optional<DocumentVersion> latestConfirmedDocument(Long workflowId, DocumentType type) {
        return documents.findTopByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(workflowId, type)
                .filter(DocumentVersion::isConfirmed);
    }

    private String selectedOptionLabel(DocumentDecision decision) {
        for (JsonNode option : decision.getOptionsJson()) {
            if (decision.getSelectedOption().equals(option.path("key").asText())) {
                return option.path("label").asText();
            }
        }
        return decision.getSelectedOption();
    }
}
