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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class AgentRequestFactory {
    private final WorkflowRepository workflows;
    private final AgentRunRepository runs;
    private final DocumentVersionRepository documents;
    private final ProjectMemberRepository members;
    private final UserRepository users;
    private final TaskRepository tasks;
    private final RepoInventoryVersionRepository inventories;
    private final RepoInventoryFileRepository inventoryFiles;
    private final int maxInventoryFiles;

    public AgentRequestFactory(WorkflowRepository workflows, AgentRunRepository runs,
                               DocumentVersionRepository documents,
                               ProjectMemberRepository members, UserRepository users,
                               TaskRepository tasks, RepoInventoryVersionRepository inventories,
                               RepoInventoryFileRepository inventoryFiles,
                               @Value("${app.code-context.max-plan-inventory-files:1000}") int maxInventoryFiles) {
        this.workflows = workflows;
        this.runs = runs;
        this.documents = documents;
        this.members = members;
        this.users = users;
        this.tasks = tasks;
        this.inventories = inventories;
        this.inventoryFiles = inventoryFiles;
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
                        ? List.of() : assignableMembers(workflow.getProjectId());
        if (run.getRunType() == AgentRunType.GENERATE_BUILD_PLAN
                && workflow.getIntentLevel() != IntentLevel.ARCHITECTURE
                && assignableMembers.isEmpty()) {
            throw new AgentProviderException(
                    "NO_ASSIGNABLE_MEMBERS", "项目中没有已完成画像且可投入的成员", false);
        }
        return new AgentGenerationRequest(workflow.getId(), run.getRunType(), workflow.getIntentLevel(),
                workflow.getTitle(), workflow.getDescription(), latest(workflow.getId(), DocumentType.DESIGN),
                latest(workflow.getId(), DocumentType.SPEC), assignableMembers, codeContext(run, workflow));
    }

    private AgentGenerationRequest.CodeContextInput codeContext(AgentRun run, Workflow workflow) {
        if (run.getRunType() != AgentRunType.GENERATE_CODE_CONTEXT_PLAN) return null;
        RepoInventoryVersion inventory = inventories.findById(run.getInventoryVersionId())
                .filter(value -> value.getStatus() == RepoInventoryStatus.CURRENT)
                .orElseThrow(() -> new AgentProviderException(
                        "REPO_INVENTORY_MISSING", "当前项目缺少可用 Repo Inventory", false));
        var files = inventoryFiles.findByInventoryVersionIdOrderByPath(inventory.getId()).stream()
                .limit(maxInventoryFiles)
                .map(file -> new AgentGenerationRequest.InventoryFileContext(file.getPath(),
                        file.getFileType().name(), file.getSizeBytes(), file.getContentHash(), file.getIndexedSummary()))
                .toList();
        return new AgentGenerationRequest.CodeContextInput(inventory.getId(), inventory.getCommitSha(),
                inventory.getRepositoryProfile(), inventory.getTreeSummary(), files);
    }

    private List<AgentGenerationRequest.MemberContext> assignableMembers(Long projectId) {
        return members.findByProjectIdAndStatus(projectId, ProjectMember.Status.ACTIVE).stream()
                .filter(ProjectMember::isProfileCompleted)
                .filter(member -> member.getCapabilityProfile() != null)
                .filter(member -> member.getWeeklyCapacityPoints() != null)
                .filter(member -> member.getAvailability() != null && !member.getAvailability().isBlank())
                .filter(member -> users.findById(member.getUserId()).map(User::isEnabled).orElse(false))
                .map(member -> new AgentGenerationRequest.MemberContext(
                        member.getUserId(), member.getProjectRole(), member.getProfileVersion(),
                        member.getCapabilityProfile().deepCopy(),
                        tasks.sumOpenEffortPoints(projectId, member.getUserId()),
                        member.getWeeklyCapacityPoints(),
                        member.getAvailability()))
                .toList();
    }

    private String latest(Long workflowId, DocumentType type) {
        return documents.findTopByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(workflowId, type)
                .map(DocumentVersion::getContent).orElse(null);
    }
}
