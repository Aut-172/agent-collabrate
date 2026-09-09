package com.example.agentcollab.service;

import com.example.agentcollab.client.AgentGenerationRequest;
import com.example.agentcollab.client.AgentProviderException;
import com.example.agentcollab.domain.*;
import com.example.agentcollab.repository.DocumentVersionRepository;
import com.example.agentcollab.repository.AgentRunRepository;
import com.example.agentcollab.repository.ProjectMemberRepository;
import com.example.agentcollab.repository.UserRepository;
import com.example.agentcollab.repository.WorkflowRepository;
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

    public AgentRequestFactory(WorkflowRepository workflows, AgentRunRepository runs,
                               DocumentVersionRepository documents,
                               ProjectMemberRepository members, UserRepository users) {
        this.workflows = workflows;
        this.runs = runs;
        this.documents = documents;
        this.members = members;
        this.users = users;
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
                latest(workflow.getId(), DocumentType.SPEC), assignableMembers);
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
                        member.getCapabilityProfile().deepCopy(), 0, member.getWeeklyCapacityPoints(),
                        member.getAvailability()))
                .toList();
    }

    private String latest(Long workflowId, DocumentType type) {
        return documents.findTopByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(workflowId, type)
                .map(DocumentVersion::getContent).orElse(null);
    }
}
