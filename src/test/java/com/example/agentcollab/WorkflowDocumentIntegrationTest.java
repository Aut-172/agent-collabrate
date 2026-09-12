package com.example.agentcollab;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.*;
import com.example.agentcollab.service.DocumentService;
import com.example.agentcollab.service.AgentRunExecutionService;
import com.example.agentcollab.service.AgentRunWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class WorkflowDocumentIntegrationTest {
    static final String TEST_PASSWORD = "pw-" + UUID.randomUUID();
    static final String DATABASE_PASSWORD = UUID.randomUUID().toString();
    static final String JWT_SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agent_collab")
            .withUsername("test")
            .withPassword(DATABASE_PASSWORD);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> JWT_SECRET);
        registry.add("app.jwt.access-token-expiration", () -> 3600000);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DocumentService documentService;
    @Autowired DocumentVersionRepository documents;
    @Autowired WorkflowMemberRepository workflowMembers;
    @Autowired WorkflowRepository workflows;
    @Autowired MemberProfileVersionRepository profileVersions;
    @Autowired ProjectMemberRepository projectMembers;
    @Autowired ProjectRepository projects;
    @Autowired UserRepository users;
    @Autowired OutboxJobRepository outboxJobs;
    @Autowired AgentRunRepository agentRuns;
    @Autowired TaskAssignmentRepository taskAssignments;
    @Autowired TaskRepository tasks;
    @Autowired com.example.agentcollab.repository.TaskPackageRepository taskPackages;
    @Autowired com.example.agentcollab.repository.TaskPackageConfirmationRepository packageConfirmations;
    @Autowired com.example.agentcollab.repository.TaskDeliveryRepository taskDeliveries;
    @Autowired com.example.agentcollab.repository.TaskBlockerRepository taskBlockers;
    @Autowired com.example.agentcollab.repository.NotificationRepository notifications;
    @Autowired com.example.agentcollab.repository.GitOperationRepository gitOperations;
    @Autowired com.example.agentcollab.repository.CiRunRepository ciRuns;
    @Autowired com.example.agentcollab.service.GitSyncService gitSync;
    @Autowired com.example.agentcollab.service.CiSyncService ciSync;
    @Autowired com.example.agentcollab.service.GitSyncWorker gitSyncWorker;
    @Autowired JdbcTemplate jdbc;
    @Autowired AgentRunWorker worker;
    @Autowired AgentRunExecutionService executions;
    @Autowired RepoInventoryVersionRepository inventories;
    @Autowired CodeContextPlanRepository contextPlans;
    @Autowired CodeContextVersionRepository codeContexts;
    @Autowired CodeContextFileRepository codeContextFiles;

    @BeforeEach
    void clearDatabase() {
        jdbc.execute("TRUNCATE TABLE outbox_jobs, users RESTART IDENTITY CASCADE");
    }

    @Test
    void createsProjectScopedWorkflowAndRejectsArchivedProject() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "core");
        long memberId = createUser(leaderToken, "member");
        addMember(leaderToken, projectId, memberId);
        String memberToken = login("member");

        long workflowId = createWorkflow(memberToken, projectId, "login flow");
        mvc.perform(get("/api/workflows/{id}", workflowId).header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intentLevel").value("FEATURE"))
                .andExpect(jsonPath("$.status").value("INTENT"))
                .andExpect(jsonPath("$.nextAction").value("GENERATE_DESIGN"));
        mvc.perform(get("/api/workflows").header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(workflowId));
        assertThat(workflowMembers.findByWorkflowIdAndUserId(workflowId, memberId)).isPresent();

        mvc.perform(post("/api/workflows/{id}/cancel", workflowId).header("Authorization", bearer(memberToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEADER_REQUIRED"));
        mvc.perform(post("/api/workflows/{id}/cancel", workflowId).header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        long outsiderId = createUser(leaderToken, "outsider");
        String outsiderToken = login("outsider");
        mvc.perform(get("/api/workflows/{id}", workflowId).header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isNotFound());
        assertThat(outsiderId).isPositive();

        var project = projects.findById(projectId).orElseThrow();
        project.archive();
        projects.save(project);
        mvc.perform(post("/api/projects/{id}/workflows", projectId)
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "title", "blocked", "description", "archived", "intentLevel", "FEATURE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
    }

    @Test
    void supportsIntentLevelsAndRestrictsParentToTheSameProject() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "core");
        long architectureId = createWorkflow(
                leaderToken, projectId, "architecture", "ARCHITECTURE", null);
        long childId = createWorkflow(
                leaderToken, projectId, "feature", "FEATURE", architectureId);
        long changeId = createWorkflow(
                leaderToken, projectId, "change", "CHANGE", null);

        mvc.perform(get("/api/workflows/{id}", childId).header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intentLevel").value("FEATURE"))
                .andExpect(jsonPath("$.completionMode").value("CI_REQUIRED"))
                .andExpect(jsonPath("$.parentWorkflowId").value(architectureId));
        mvc.perform(get("/api/workflows/{id}", architectureId).header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionMode").value("ARCHITECTURE_BASELINE"));
        mvc.perform(get("/api/workflows/{id}", changeId).header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextAction").value("GENERATE_BUILD_PLAN"));
        long invalidRunId = createAgentRun(changeId, AgentRunType.GENERATE_DESIGN);
        assertThatThrownBy(() -> documentService.recordGeneratedDesign(changeId, "# Invalid design", invalidRunId))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("INVALID_WORKFLOW_TRANSITION");
        assertThat(documents.findByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(changeId, DocumentType.DESIGN))
                .isEmpty();

        long otherProjectId = createProject(leaderToken, "other");
        mvc.perform(post("/api/projects/{id}/workflows", otherProjectId)
                        .header("Authorization", bearer(leaderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "title", "invalid child",
                                "description", "cross-project parent",
                                "intentLevel", "FEATURE",
                                "parentWorkflowId", architectureId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PARENT_WORKFLOW_NOT_FOUND"));

        mvc.perform(post("/api/projects/{id}/workflows", projectId)
                        .header("Authorization", bearer(leaderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "title", "missing level", "description", "invalid"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsOneLeaderApprovedCiBootstrapAndRejectsBypasses() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "bootstrap");
        long memberId = createUser(leaderToken, "member");
        addMember(leaderToken, projectId, memberId);
        String memberToken = login("member");

        mvc.perform(post("/api/projects/{id}/ci-bootstrap", projectId)
                        .header("Authorization", bearer(memberToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "title", "unauthorized bootstrap",
                                "description", "must be leader approved"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEADER_REQUIRED"));

        mvc.perform(post("/api/projects/{id}/workflows", projectId)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "title", "wrong bootstrap endpoint",
                                "description", "must use the dedicated endpoint",
                                "intentLevel", "FEATURE",
                                "completionMode", "CI_BOOTSTRAP"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CI_BOOTSTRAP_ENDPOINT_REQUIRED"));

        long bootstrapId = createCiBootstrap(leaderToken, projectId, "establish project and CI");
        mvc.perform(get("/api/workflows/{id}", bootstrapId).header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intentLevel").value("FEATURE"))
                .andExpect(jsonPath("$.completionMode").value("CI_BOOTSTRAP"));

        mvc.perform(post("/api/projects/{id}/ci-bootstrap", projectId)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "title", "duplicate bootstrap",
                                "description", "must be unique"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CI_BOOTSTRAP_EXISTS"));

    }

    @Test
    void ciBootstrapTasksStayAssignedToTheWorkflowCreatorLeader() throws Exception {
        String leaderToken = initialize("leader");
        long leaderId = users.findByUsername("leader").orElseThrow().getId();
        long projectId = createProject(leaderToken, "bootstrap-owner");
        updateProfile(leaderToken, projectId, 13);
        long memberId = createUser(leaderToken, "member");
        addMember(leaderToken, projectId, memberId);
        String memberToken = login("member");
        updateProfile(memberToken, projectId, 8);
        long workflowId = createCiBootstrap(leaderToken, projectId, "initialize project and CI");

        advanceToBuildPlan(leaderToken, workflowId);
        var generated = documents.findByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(
                workflowId, DocumentType.BUILD_PLAN).get(0);
        var plan = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(generated.getContent());
        assertThat(plan.path("assignments").get(0).path("userId").asLong()).isEqualTo(leaderId);

        var memberAssignment = (com.fasterxml.jackson.databind.node.ObjectNode) plan.deepCopy();
        var assignment = (com.fasterxml.jackson.databind.node.ObjectNode)
                memberAssignment.path("assignments").get(0);
        assignment.put("userId", memberId);
        assignment.put("projectRole", "MEMBER");
        assignment.put("profileVersion", 1);
        assignment.withObject("workloadSnapshot").put("weeklyCapacityPoints", 8);
        mvc.perform(put("/api/workflows/{id}/plan-drafts", workflowId)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("content", memberAssignment.toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CI_BOOTSTRAP_LEADER_ASSIGNEE_REQUIRED"));

        confirm(leaderToken, workflowId, "approve-plan", 1).andExpect(status().isOk());
        mvc.perform(post("/api/workflows/{id}/create-tasks", workflowId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskCount").value(1));
        Task task = tasks.findByWorkflowIdOrderById(workflowId).get(0);
        TaskAssignment taskAssignment = taskAssignments.findByTaskIdAndCurrentTrue(task.getId()).orElseThrow();
        assertThat(taskAssignment.getAssigneeUserId()).isEqualTo(leaderId);
        assertThat(taskAssignment.getAssignmentReason()).contains("项目 Leader");
        assertThat(taskAssignment.getAssignmentScore()).isNull();
    }

    @Test
    void keepsDocumentVersionsAndRequiresCreatorToConfirmLatestVersion() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "core");
        long creatorId = createUser(leaderToken, "creator");
        addMember(leaderToken, projectId, creatorId);
        String creatorToken = login("creator");
        long workflowId = createWorkflow(creatorToken, projectId, "document flow");

        long designRunId = createAgentRun(workflowId, AgentRunType.GENERATE_DESIGN);
        var designV1 = documentService.recordGeneratedDesign(workflowId, "# Design v1", designRunId);
        assertThat(designV1.getVersionNo()).isEqualTo(1);
        assertThatThrownBy(() -> documentService.recordGeneratedSpec(workflowId, "# Spec", 102L))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("DOCUMENT_NOT_CONFIRMED");

        mvc.perform(put("/api/workflows/{id}/design", workflowId)
                        .header("Authorization", bearer(leaderToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"# Design v2\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionNo").value(2));
        mvc.perform(put("/api/workflows/{id}/design", workflowId)
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"# Design v3\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionNo").value(3));

        confirm(creatorToken, workflowId, "confirm-design", 2).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_VERSION_STALE"));
        confirm(leaderToken, workflowId, "confirm-design", 3).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKFLOW_CREATOR_REQUIRED"));
        confirm(creatorToken, workflowId, "confirm-design", 3).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(true));

        long specRunId = createAgentRun(workflowId, AgentRunType.GENERATE_SPEC);
        documentService.recordGeneratedSpec(workflowId, "# Spec v1", specRunId);
        mvc.perform(put("/api/workflows/{id}/spec", workflowId)
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"# Spec v2\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionNo").value(2));
        confirm(creatorToken, workflowId, "confirm-spec", 1).andExpect(status().isConflict());
        confirm(creatorToken, workflowId, "confirm-spec", 2).andExpect(status().isOk());

        mvc.perform(get("/api/workflows/{id}", workflowId).header("Authorization", bearer(creatorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SPEC_CONFIRMED"))
                .andExpect(jsonPath("$.nextAction").value("GENERATE_BUILD_PLAN"));
        var designVersions = documents.findByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(workflowId, DocumentType.DESIGN);
        assertThat(designVersions).extracting(value -> value.getVersionNo()).containsExactly(3, 2, 1);
        assertThat(designVersions).extracting(value -> value.getContent())
                .containsExactly("# Design v3", "# Design v2", "# Design v1");
    }

    @Test
    void queuesAgentRunAndPersistsOutputOnlyWhenWorkerCompletes() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "core");
        long workflowId = createWorkflow(leaderToken, projectId, "async design");

        long runId = requestRun(leaderToken, workflowId, "generate-design");
        long duplicateRunId = requestRun(leaderToken, workflowId, "generate-design");
        assertThat(duplicateRunId).isEqualTo(runId);
        assertThat(documents.findByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(
                workflowId, DocumentType.DESIGN)).isEmpty();
        assertThat(outboxJobs.count()).isEqualTo(1);

        assertThat(worker.processNext()).isTrue();
        mvc.perform(get("/api/agent-runs/{id}", runId).header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.responseSummary").value("Generated Design document"));
        mvc.perform(get("/api/workflows/{id}", workflowId).header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DESIGN_PROPOSED"));
        assertThat(documents.findByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(
                        workflowId, DocumentType.DESIGN))
                .singleElement()
                .extracting(value -> value.getAgentRunId()).isEqualTo(runId);

        requestRunExpecting(leaderToken, workflowId, "generate-spec", status().isConflict());
        confirm(leaderToken, workflowId, "confirm-design", 1).andExpect(status().isOk());
        long specRunId = requestRun(leaderToken, workflowId, "generate-spec");
        assertThat(worker.processNext()).isTrue();
        assertThat(agentRuns.findById(specRunId).orElseThrow().getStatus()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(workflows.findById(workflowId).orElseThrow().getStatus().name())
                .isEqualTo("SPEC_PROPOSED");

        long outsiderId = createUser(leaderToken, "outsider");
        String outsiderToken = login("outsider");
        assertThat(outsiderId).isPositive();
        mvc.perform(get("/api/agent-runs/{id}", runId).header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isNotFound());
        assertThat(worker.processNext()).isFalse();
    }

    @Test
    void formalGenerationRequiresCurrentContextAndPersistsItsReference() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "context-gate");
        long workflowId = createWorkflow(leaderToken, projectId, "context-bound design");

        mvc.perform(post("/api/workflows/{id}/generate-design", workflowId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CODE_CONTEXT_REQUIRED"));

        long staleContextId = ensureCurrentCodeContext(workflowId);
        long staleRunId = requestRun(leaderToken, workflowId, "generate-design");
        CodeContextVersion staleContext = codeContexts.findById(staleContextId).orElseThrow();
        staleContext.markStale();
        codeContexts.save(staleContext);

        assertThat(worker.processNext()).isTrue();
        assertThat(agentRuns.findById(staleRunId).orElseThrow().getErrorCode()).isEqualTo("CODE_CONTEXT_STALE");
        assertThat(documents.findByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(
                workflowId, DocumentType.DESIGN)).isEmpty();

        long currentContextId = ensureCurrentCodeContext(workflowId);
        long currentRunId = requestRun(leaderToken, workflowId, "generate-design");
        AgentRun currentRun = agentRuns.findById(currentRunId).orElseThrow();
        assertThat(currentRun.getCodeContextVersionId()).isEqualTo(currentContextId);
        assertThat(currentRun.getContextPlanId()).isNotNull();
        assertThat(currentRun.getInventoryVersionId()).isNotNull();
        assertThat(worker.processNext()).isTrue();

        DocumentVersion generated = documents.findByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(
                workflowId, DocumentType.DESIGN).get(0);
        assertThat(generated.getAgentRunId()).isEqualTo(currentRunId);
        assertThat(generated.getCodeContextVersionId()).isEqualTo(currentContextId);
        mvc.perform(get("/api/workflows/{id}/documents", workflowId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codeContextVersionId").value(currentContextId));
    }

    @Test
    void regenerationCreatesNewRunsAndDocumentVersionsWithoutChangingCandidateState() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "core");
        long workflowId = createWorkflow(leaderToken, projectId, "regeneration", "ARCHITECTURE", null);

        long designRun1 = requestRun(leaderToken, workflowId, "generate-design");
        assertThat(worker.processNext()).isTrue();
        long designRun2 = requestRun(leaderToken, workflowId, "generate-design");
        assertThat(designRun2).isNotEqualTo(designRun1);
        assertThat(worker.processNext()).isTrue();
        assertDocumentVersions(workflowId, DocumentType.DESIGN, designRun2, designRun1);
        assertThat(workflows.findById(workflowId).orElseThrow().getStatus().name())
                .isEqualTo("DESIGN_PROPOSED");

        confirm(leaderToken, workflowId, "confirm-design", 2).andExpect(status().isOk());
        long specRun1 = requestRun(leaderToken, workflowId, "generate-spec");
        assertThat(worker.processNext()).isTrue();
        long specRun2 = requestRun(leaderToken, workflowId, "generate-spec");
        assertThat(specRun2).isNotEqualTo(specRun1);
        assertThat(worker.processNext()).isTrue();
        assertDocumentVersions(workflowId, DocumentType.SPEC, specRun2, specRun1);
        assertThat(workflows.findById(workflowId).orElseThrow().getStatus().name())
                .isEqualTo("SPEC_PROPOSED");

        confirm(leaderToken, workflowId, "confirm-spec", 2).andExpect(status().isOk());
        long planRun1 = requestRun(leaderToken, workflowId, "generate-build-plan");
        assertThat(worker.processNext()).isTrue();
        long planRun2 = requestRun(leaderToken, workflowId, "generate-build-plan");
        assertThat(planRun2).isNotEqualTo(planRun1);
        assertThat(worker.processNext()).isTrue();
        assertDocumentVersions(workflowId, DocumentType.BUILD_PLAN, planRun2, planRun1);
        assertThat(workflows.findById(workflowId).orElseThrow().getStatus().name())
                .isEqualTo("BUILD_PLAN_PROPOSED");
    }

    @Test
    void retriesTransientFailureOnceAndAllowsManualRetryThenCancellation() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "core");
        long workflowId = createWorkflow(leaderToken, projectId, "retry design");
        long runId = requestRun(leaderToken, workflowId, "generate-design");

        var firstClaim = executions.claimNext().orElseThrow();
        assertThat(executions.claimNext()).isEmpty();
        executions.handleFailure(firstClaim, "PROVIDER_UNAVAILABLE", "Temporary provider failure", true);
        assertThat(agentRuns.findById(runId).orElseThrow().getStatus()).isEqualTo(AgentRunStatus.RUNNING);
        assertThat(agentRuns.findById(runId).orElseThrow().getRetryCount()).isEqualTo(1);
        assertThat(outboxJobs.findById(firstClaim.jobId()).orElseThrow().getStatus())
                .isEqualTo(OutboxJobStatus.PENDING);

        var secondClaim = executions.claimNext().orElseThrow();
        executions.handleFailure(secondClaim, "PROVIDER_UNAVAILABLE", "Temporary provider failure", true);
        assertThat(agentRuns.findById(runId).orElseThrow().getStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(workflows.findById(workflowId).orElseThrow().getStatus().name()).isEqualTo("INTENT");

        String retryBody = mvc.perform(post("/api/agent-runs/{id}/retry", runId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();
        long retryRunId = json.readTree(retryBody).get("runId").asLong();
        assertThat(retryRunId).isNotEqualTo(runId);

        long memberId = createUser(leaderToken, "member");
        addMember(leaderToken, projectId, memberId);
        String memberToken = login("member");
        mvc.perform(post("/api/agent-runs/{id}/cancel", retryRunId)
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEADER_REQUIRED"));
        mvc.perform(post("/api/agent-runs/{id}/cancel", retryRunId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void changeBuildPlanUsesCurrentProjectMemberProfileAndCapacity() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "core");
        updateProfile(leaderToken, projectId, 13);
        long workflowId = createWorkflow(leaderToken, projectId, "small change", "CHANGE", null);

        long runId = requestRun(leaderToken, workflowId, "generate-build-plan");
        assertThat(worker.processNext()).isTrue();
        assertThat(agentRuns.findById(runId).orElseThrow().getStatus()).isEqualTo(AgentRunStatus.SUCCEEDED);
        var plan = documents.findByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(
                workflowId, DocumentType.BUILD_PLAN).get(0);
        var content = json.readTree(plan.getContent());
        assertThat(content.path("intentLevel").asText()).isEqualTo("CHANGE");
        assertThat(content.path("assignments").get(0).path("profileVersion").asInt()).isEqualTo(1);
        assertThat(content.path("assignments").get(0).path("workloadSnapshot")
                .path("weeklyCapacityPoints").asInt()).isEqualTo(13);
        assertThat(workflows.findById(workflowId).orElseThrow().getStatus().name())
                .isEqualTo("BUILD_PLAN_PROPOSED");

        updateProfile(leaderToken, projectId, 21);
        long regeneratedRunId = requestRun(leaderToken, workflowId, "generate-build-plan");
        assertThat(worker.processNext()).isTrue();
        assertThat(regeneratedRunId).isNotEqualTo(runId);
        var regenerated = documents.findByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(
                workflowId, DocumentType.BUILD_PLAN).get(0);
        var regeneratedContent = json.readTree(regenerated.getContent());
        assertThat(regenerated.getVersionNo()).isEqualTo(2);
        assertThat(regeneratedContent.path("assignments").get(0).path("profileVersion").asInt()).isEqualTo(2);
        assertThat(regeneratedContent.path("assignments").get(0).path("workloadSnapshot")
                .path("weeklyCapacityPoints").asInt()).isEqualTo(21);
    }

    @Test
    void failedAgentOutputDoesNotAdvanceWorkflowAndWorkflowCancellationStopsQueuedRuns() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "core");
        long changeId = createWorkflow(leaderToken, projectId, "unassignable change", "CHANGE", null);
        long failedRunId = requestRun(leaderToken, changeId, "generate-build-plan");

        assertThat(worker.processNext()).isTrue();
        AgentRun failedRun = agentRuns.findById(failedRunId).orElseThrow();
        assertThat(failedRun.getStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(failedRun.getErrorCode()).isEqualTo("NO_ASSIGNABLE_MEMBERS");
        assertThat(workflows.findById(changeId).orElseThrow().getStatus().name()).isEqualTo("INTENT");
        assertThat(documents.findByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(
                changeId, DocumentType.BUILD_PLAN)).isEmpty();

        long featureId = createWorkflow(leaderToken, projectId, "cancelled feature");
        long queuedRunId = requestRun(leaderToken, featureId, "generate-design");
        mvc.perform(post("/api/workflows/{id}/cancel", featureId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(agentRuns.findById(queuedRunId).orElseThrow().getStatus())
                .isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(outboxJobs.findByJobTypeAndReferenceId(
                        com.example.agentcollab.domain.OutboxJobType.AGENT_RUN, queuedRunId).orElseThrow().getStatus())
                .isEqualTo(OutboxJobStatus.FAILED);
    }

    @Test
    void leaderEditsAndApprovesLatestPlanThenCreatesTasksWithAssignmentSnapshots() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "core");
        Project project = projects.findById(projectId).orElseThrow();
        project.enableCi();
        projects.save(project);
        updateProfile(leaderToken, projectId, 13);
        long memberId = createUser(leaderToken, "member");
        addMember(leaderToken, projectId, memberId);
        String memberToken = login("member");
        updateProfile(memberToken, projectId, 8);
        long workflowId = createWorkflow(leaderToken, projectId, "assignable change", "CHANGE", null);

        requestRun(leaderToken, workflowId, "generate-build-plan");
        assertThat(worker.processNext()).isTrue();
        mvc.perform(post("/api/workflows/{id}/create-tasks", workflowId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isConflict());

        var generated = documents.findByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(
                workflowId, DocumentType.BUILD_PLAN).get(0);
        var edited = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(generated.getContent());
        ((com.fasterxml.jackson.databind.node.ObjectNode) edited.path("tasks").get(0))
                .put("title", "Leader reviewed task");
        var assignment = (com.fasterxml.jackson.databind.node.ObjectNode) edited.path("assignments").get(0);
        assignment.put("userId", memberId);
        assignment.put("projectRole", "MEMBER");
        assignment.put("profileVersion", 1);
        assignment.put("fitReason", "Leader selected the member for the task");
        assignment.withObject("workloadSnapshot").put("weeklyCapacityPoints", 8);

        mvc.perform(put("/api/workflows/{id}/plan-drafts", workflowId)
                        .header("Authorization", bearer(memberToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("content", edited.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEADER_REQUIRED"));
        mvc.perform(put("/api/workflows/{id}/plan-drafts", workflowId)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("content", edited.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(2));
        confirm(leaderToken, workflowId, "approve-plan", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_VERSION_STALE"));
        confirm(leaderToken, workflowId, "approve-plan", 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(true));

        updateProfile(memberToken, projectId, 20);
        mvc.perform(post("/api/workflows/{id}/create-tasks", workflowId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskCount").value(1))
                .andExpect(jsonPath("$.workflowStatus").value("TASKS_READY"));
        mvc.perform(post("/api/workflows/{id}/create-tasks", workflowId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskCount").value(1));
        assertThat(tasks.count()).isEqualTo(1);

        var task = tasks.findByWorkflowIdOrderById(workflowId).get(0);
        mvc.perform(get("/api/tasks/{id}/packages/current", task.getId())
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packageVersion").value(1))
                .andExpect(jsonPath("$.status").value("CURRENT"))
                .andExpect(jsonPath("$.contentHash").value(org.hamcrest.Matchers.startsWith("sha256:")))
                .andExpect(jsonPath("$.contentJson.task.taskId").value("TASK-001"))
                .andExpect(jsonPath("$.contentJson.task.packageId").isNumber())
                .andExpect(jsonPath("$.contentJson.task.packageHash").value(org.hamcrest.Matchers.startsWith("sha256:")))
                .andExpect(jsonPath("$.baseCommit").value("1111111111111111111111111111111111111111"))
                .andExpect(jsonPath("$.codeContextVersionId").isNumber())
                .andExpect(jsonPath("$.contextPlanId").isNumber())
                .andExpect(jsonPath("$.contentJson.context.baseCommitSha")
                        .value("1111111111111111111111111111111111111111"))
                .andExpect(jsonPath("$.contentJson.context.codeContextVersionId").isNumber())
                .andExpect(jsonPath("$.contentJson.context.contextPlanId").isNumber())
                .andExpect(jsonPath("$.contentJson.context.relevantPaths[0]").value("src/main/java/example/App.java"))
                .andExpect(jsonPath("$.contentJson.context.codeEvidence[0].reason").value("Existing application entry point"))
                .andExpect(jsonPath("$.contentMarkdown").value(org.hamcrest.Matchers.containsString("Agent 任务包")))
                .andExpect(jsonPath("$.contentMarkdown").value(org.hamcrest.Matchers.containsString("## Git 执行策略")))
                .andExpect(jsonPath("$.contentMarkdown").value(org.hamcrest.Matchers.containsString("## 最终报告")))
                .andExpect(jsonPath("$.contentMarkdown").value(org.hamcrest.Matchers.containsString("只输出有效 JSON")))
                .andExpect(jsonPath("$.contentMarkdown").value(org.hamcrest.Matchers.containsString("\"schemaVersion\" : \"1.0\"")))
                .andExpect(jsonPath("$.contentMarkdown").value(org.hamcrest.Matchers.containsString("\"taskId\" :")));
        var initialPackage = taskPackages.findByTaskIdAndStatus(task.getId(),
                com.example.agentcollab.domain.TaskPackageStatus.CURRENT).orElseThrow();
        mvc.perform(post("/api/tasks/{id}/packages/{version}/confirm", task.getId(), 1)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "packageId", initialPackage.getId(), "contentHash", initialPackage.getContentHash()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TASK_ASSIGNEE_REQUIRED"));
        mvc.perform(post("/api/tasks/{id}/packages/{version}/confirm", task.getId(), 1)
                        .header("Authorization", bearer(memberToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "packageId", initialPackage.getId(), "contentHash", "sha256:wrong"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_PACKAGE_STALE"))
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andExpect(jsonPath("$.submittedVersion").value(1));
        mvc.perform(post("/api/tasks/{id}/packages/{version}/confirm", task.getId(), 1)
                        .header("Authorization", bearer(memberToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "packageId", initialPackage.getId(), "contentHash", initialPackage.getContentHash()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.packageVersion").value(1));
        mvc.perform(post("/api/tasks/{id}/packages/{version}/confirm", task.getId(), 1)
                        .header("Authorization", bearer(memberToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "packageId", initialPackage.getId(), "contentHash", initialPackage.getContentHash()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskStatus").value("IN_PROGRESS"));
        assertThat(packageConfirmations.count()).isEqualTo(1);
        assertThat(workflows.findById(workflowId).orElseThrow().getStatus().name()).isEqualTo("IN_PROGRESS");
        var initialAssignment = taskAssignments.findByTaskIdAndCurrentTrue(task.getId()).orElseThrow();
        assertThat(task.getStatus().name()).isEqualTo("ASSIGNED");
        assertThat(task.getSourcePlanVersion()).isEqualTo(2);
        assertThat(task.getSourceSpecVersion()).isNull();
        assertThat(initialAssignment.getAssigneeUserId()).isEqualTo(memberId);
        assertThat(initialAssignment.getProfileVersion()).isEqualTo(2);
        assertThat(initialAssignment.getWorkloadSnapshot().path("weeklyCapacityPoints").asInt()).isEqualTo(20);
        mvc.perform(delete("/api/projects/{id}/members/{userId}", projectId, memberId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_OPEN_TASKS"));

        updateProfile(memberToken, projectId, 25);
        assertThat(taskAssignments.findById(initialAssignment.getId()).orElseThrow().getProfileVersion()).isEqualTo(2);
        mvc.perform(put("/api/tasks/{id}/assignee", task.getId())
                        .header("Authorization", bearer(memberToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "assigneeUserId", memberId,
                                "reason", "Unauthorized reassignment"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEADER_REQUIRED"));
        mvc.perform(put("/api/tasks/{id}/assignee", task.getId())
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "assigneeUserId", users.findByUsername("leader").orElseThrow().getId(),
                                "reason", "Leader takes ownership",
                                "assignmentScore", 0.75))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentAssignment.assigneeUserId")
                        .value(users.findByUsername("leader").orElseThrow().getId()))
                .andExpect(jsonPath("$.currentAssignment.profileVersion").value(1))
                .andExpect(jsonPath("$.planDetails.acceptanceCriteria[0]").isNotEmpty());
        var replacementPackage = taskPackages.findByTaskIdAndStatus(task.getId(),
                com.example.agentcollab.domain.TaskPackageStatus.CURRENT).orElseThrow();
        assertThat(replacementPackage.getPackageVersion()).isEqualTo(2);
        assertThat(replacementPackage.getContentJson().path("task").path("packageId").asLong())
                .isEqualTo(replacementPackage.getId());
        assertThat(taskPackages.findById(initialPackage.getId()).orElseThrow().getStatus().name()).isEqualTo("STALE");
        mvc.perform(post("/api/tasks/{id}/packages/{version}/confirm", task.getId(), 1)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "packageId", initialPackage.getId(), "contentHash", initialPackage.getContentHash()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_PACKAGE_STALE"))
                .andExpect(jsonPath("$.currentVersion").value(2))
                .andExpect(jsonPath("$.submittedVersion").value(1));
        mvc.perform(post("/api/tasks/{id}/packages/{version}/confirm", task.getId(), 2)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "packageId", replacementPackage.getId(),
                                "contentHash", replacementPackage.getContentHash()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskStatus").value("IN_PROGRESS"));
        assertThat(taskPackages.findById(initialPackage.getId()).orElseThrow().getSupersededBy())
                .isEqualTo(replacementPackage.getId());
        mvc.perform(post("/api/tasks/{id}/packages/{version}/confirm", task.getId(), 1)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "packageId", initialPackage.getId(), "contentHash", initialPackage.getContentHash()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_PACKAGE_STALE"));
        mvc.perform(post("/api/tasks/{id}/packages/{version}/confirm", task.getId(), 2)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "packageId", replacementPackage.getId(), "contentHash", replacementPackage.getContentHash()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskStatus").value("IN_PROGRESS"));
        assertThat(taskAssignments.findByTaskIdOrderByAssignmentVersionDesc(task.getId())).hasSize(2);
        assertThat(taskAssignments.findById(initialAssignment.getId()).orElseThrow().isCurrent()).isFalse();
        mvc.perform(delete("/api/projects/{id}/members/{userId}", projectId, memberId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/workflows/{id}/cancel", workflowId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(tasks.findById(task.getId()).orElseThrow().getStatus().name()).isEqualTo("CANCELLED");
        assertThat(taskPackages.findById(replacementPackage.getId()).orElseThrow().getStatus().name())
                .isEqualTo("RETIRED");
    }

    @Test
    void reportsResolvesAndResumesTaskBlockerWithANewTaskPackage() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "blocker");
        updateProfile(leaderToken, projectId, 13);
        long memberId = createUser(leaderToken, "member");
        addMember(leaderToken, projectId, memberId);
        String memberToken = login("member");
        long workflowId = createCiBootstrap(leaderToken, projectId, "blocked initialization");

        advanceToBuildPlan(leaderToken, workflowId);
        confirm(leaderToken, workflowId, "approve-plan", 1).andExpect(status().isOk());
        mvc.perform(post("/api/workflows/{id}/create-tasks", workflowId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk());
        Task task = tasks.findByWorkflowIdOrderById(workflowId).get(0);
        mvc.perform(get("/api/workflows/{id}/board", workflowId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns.length()").value(8))
                .andExpect(jsonPath("$.columns[1].key").value("ASSIGNED"))
                .andExpect(jsonPath("$.columns[1].cards[0].externalKey").value(task.getExternalKey()));
        TaskPackage firstPackage = taskPackages.findByTaskIdAndStatus(
                task.getId(), TaskPackageStatus.CURRENT).orElseThrow();
        mvc.perform(post("/api/tasks/{id}/packages/{version}/confirm", task.getId(), 1)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "packageId", firstPackage.getId(),
                                "contentHash", firstPackage.getContentHash()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationType").value("START_DEVELOPMENT"));

        var report = new java.util.LinkedHashMap<String, Object>();
        report.put("reasonCode", "SPEC_CONFLICT");
        report.put("summary", "The approved specification conflicts with the repository contract");
        report.put("details", "The current API requires an immutable identifier.");
        report.put("evidence", java.util.List.of("src/main/java/example/App.java"));
        report.put("question", "Should the task preserve the existing identifier contract?");

        mvc.perform(post("/api/tasks/{id}/block", task.getId())
                        .header("Authorization", bearer(memberToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(report)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TASK_ASSIGNEE_REQUIRED"));

        String blockerBody = mvc.perform(post("/api/tasks/{id}/block", task.getId())
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(report)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.taskStatus").value("BLOCKED"))
                .andExpect(jsonPath("$.workflowHealth").value("NEEDS_ATTENTION"))
                .andReturn().getResponse().getContentAsString();
        long blockerId = json.readTree(blockerBody).path("id").asLong();
        mvc.perform(get("/api/notifications").header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        String notificationBody = mvc.perform(get("/api/notifications")
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        long notificationId = json.readTree(notificationBody).get(0).path("id").asLong();
        mvc.perform(post("/api/notifications/{id}/read", notificationId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.readAt").isNotEmpty());
        mvc.perform(post("/api/notifications/{id}/read", notificationId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/notifications/{id}/read", notificationId)
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isNotFound());
        assertThat(workflows.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.IN_PROGRESS);

        mvc.perform(post("/api/tasks/{id}/block", task.getId())
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(report)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_BLOCKER_ALREADY_OPEN"));
        mvc.perform(post("/api/tasks/{taskId}/blockers/{blockerId}/resolve", task.getId(), blockerId)
                        .header("Authorization", bearer(memberToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("resolution", "Keep the contract"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TASK_BLOCKER_RESOLVE_FORBIDDEN"));
        mvc.perform(post("/api/tasks/{id}/packages/{version}/confirm", task.getId(), 1)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "packageId", firstPackage.getId(),
                                "contentHash", firstPackage.getContentHash()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_BLOCKER_OPEN"));

        jdbc.update("UPDATE workflows SET status = 'READY_TO_CLOSE' WHERE id = ?", workflowId);
        mvc.perform(post("/api/workflows/{id}/close", workflowId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKFLOW_HAS_OPEN_BLOCKERS"));
        jdbc.update("UPDATE workflows SET status = 'IN_PROGRESS' WHERE id = ?", workflowId);

        mvc.perform(post("/api/tasks/{taskId}/blockers/{blockerId}/resolve", task.getId(), blockerId)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "resolution", "Preserve the immutable identifier contract"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.taskStatus").value("BLOCKED"))
                .andExpect(jsonPath("$.workflowHealth").value("HEALTHY"))
                .andExpect(jsonPath("$.currentPackageVersion").value(2));

        TaskPackage secondPackage = taskPackages.findByTaskIdAndStatus(
                task.getId(), TaskPackageStatus.CURRENT).orElseThrow();
        assertThat(secondPackage.getPackageVersion()).isEqualTo(2);
        assertThat(taskPackages.findById(firstPackage.getId()).orElseThrow().getStatus())
                .isEqualTo(TaskPackageStatus.STALE);
        assertThat(secondPackage.getContentJson().path("blockerHistory")).singleElement().satisfies(item -> {
            assertThat(item.path("reasonCode").asText()).isEqualTo("SPEC_CONFLICT");
            assertThat(item.path("status").asText()).isEqualTo("RESOLVED");
            assertThat(item.path("resolution").asText()).isEqualTo("Preserve the immutable identifier contract");
        });
        assertThat(secondPackage.getContentMarkdown()).contains("## 已解决的阻塞", "SPEC_CONFLICT", "解决说明：");

        mvc.perform(get("/api/tasks/{id}/blockers", task.getId())
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(blockerId))
                .andExpect(jsonPath("$[0].status").value("RESOLVED"));
        mvc.perform(post("/api/tasks/{id}/packages/{version}/confirm", task.getId(), 2)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "packageId", secondPackage.getId(),
                                "contentHash", secondPackage.getContentHash()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationType").value("RESUME_AFTER_BLOCKER"))
                .andExpect(jsonPath("$.taskStatus").value("IN_PROGRESS"));
        assertThat(taskBlockers.findByTaskIdOrderByCreatedAtDesc(task.getId())).singleElement()
                .satisfies(blocker -> {
                    assertThat(blocker.getResolvedBy()).isEqualTo(users.findByUsername("leader").orElseThrow().getId());
                    assertThat(blocker.getResolvedAt()).isNotNull();
                });
    }

    @Test
    void validatesAndRegistersFinalReportWithoutTrustingItsCiClaims() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "delivery");
        updateProfile(leaderToken, projectId, 13);
        long reviewerId = createUser(leaderToken, "reviewer");
        addMember(leaderToken, projectId, reviewerId);
        String reviewerToken = login("reviewer");
        long workflowId = createCiBootstrap(leaderToken, projectId, "initial delivery");

        advanceToBuildPlan(leaderToken, workflowId);
        confirm(leaderToken, workflowId, "approve-plan", 1).andExpect(status().isOk());
        mvc.perform(post("/api/workflows/{id}/create-tasks", workflowId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk());

        var task = tasks.findByWorkflowIdOrderById(workflowId).get(0);
        var taskPackage = taskPackages.findByTaskIdAndStatus(task.getId(),
                com.example.agentcollab.domain.TaskPackageStatus.CURRENT).orElseThrow();
        mvc.perform(post("/api/tasks/{id}/packages/{version}/confirm", task.getId(), 1)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "packageId", taskPackage.getId(),
                                "contentHash", taskPackage.getContentHash()))))
                .andExpect(status().isOk());

        String commitSha = "0123456789abcdef0123456789abcdef01234567";
        var validReport = finalReport(task, taskPackage, commitSha);
        var validRequest = deliveryRequest(taskPackage, validReport, task.getBranchName(), commitSha);

        mvc.perform(post("/api/tasks/{id}/delivery", task.getId())
                        .header("Authorization", bearer(reviewerToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TASK_ASSIGNEE_REQUIRED"));

        var invalidReport = validReport.deepCopy();
        invalidReport.remove("summary");
        mvc.perform(post("/api/tasks/{id}/delivery", task.getId())
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(deliveryRequest(
                                taskPackage, invalidReport, task.getBranchName(), commitSha))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FINAL_REPORT"));

        var staleReport = validReport.deepCopy();
        staleReport.put("packageVersion", 2);
        mvc.perform(post("/api/tasks/{id}/delivery", task.getId())
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(deliveryRequest(
                                taskPackage, staleReport, task.getBranchName(), commitSha))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_PACKAGE_STALE"))
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andExpect(jsonPath("$.submittedVersion").value(2));

        var mismatchedGitReport = validReport.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) mismatchedGitReport.path("git"))
                .put("commitSha", "abcdefabcdefabcdefabcdefabcdefabcdefabcd");
        mvc.perform(post("/api/tasks/{id}/delivery", task.getId())
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(deliveryRequest(
                                taskPackage, mismatchedGitReport, task.getBranchName(), commitSha))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINAL_REPORT_GIT_MISMATCH"));

        var mismatchedContextReport = validReport.deepCopy();
        mismatchedContextReport.put("codeContextVersionId", taskPackage.getCodeContextVersionId() + 1);
        mvc.perform(post("/api/tasks/{id}/delivery", task.getId())
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(deliveryRequest(
                                taskPackage, mismatchedContextReport, task.getBranchName(), commitSha))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINAL_REPORT_CONTEXT_MISMATCH"));

        String response = mvc.perform(post("/api/tasks/{id}/delivery", task.getId())
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.packageId").value(taskPackage.getId()))
                .andExpect(jsonPath("$.packageVersion").value(1))
                .andExpect(jsonPath("$.codeContextVersionId").value(taskPackage.getCodeContextVersionId()))
                .andExpect(jsonPath("$.contextPlanId").value(taskPackage.getContextPlanId()))
                .andExpect(jsonPath("$.baseCommitSha").value(taskPackage.getBaseCommit()))
                .andExpect(jsonPath("$.commitSha").value(commitSha))
                .andReturn().getResponse().getContentAsString();
        long deliveryId = json.readTree(response).path("id").asLong();

        assertThat(tasks.findById(task.getId()).orElseThrow().getStatus().name())
                .isEqualTo("DELIVERY_SUBMITTED");
        assertThat(workflows.findById(workflowId).orElseThrow().getStatus().name())
                .isEqualTo("DELIVERY_SUBMITTED");
        var saved = taskDeliveries.findById(deliveryId).orElseThrow();
        assertThat(saved.getPackageId()).isEqualTo(taskPackage.getId());
        assertThat(saved.getPackageVersion()).isEqualTo(1);
        assertThat(saved.getCodeContextVersionId()).isEqualTo(taskPackage.getCodeContextVersionId());
        assertThat(saved.getContextPlanId()).isEqualTo(taskPackage.getContextPlanId());
        assertThat(saved.getBaseCommitSha()).isEqualTo(taskPackage.getBaseCommit());
        assertThat(saved.getReportJson().path("tests").get(0).path("status").asText()).isEqualTo("PASSED");
        assertThat(saved.getStatus().name()).isEqualTo("SUBMITTED");
        assertThat(outboxJobs.findByJobTypeAndReferenceId(OutboxJobType.GIT_SYNC, deliveryId))
                .get().extracting(job -> job.getStatus()).isEqualTo(OutboxJobStatus.PENDING);

        assertThat(gitOperations.findByDeliveryId(deliveryId)).get()
                .satisfies(operation -> {
                    assertThat(operation.getStatus()).isEqualTo(com.example.agentcollab.domain.GitOperationStatus.PENDING);
                    assertThat(operation.getCommitSha()).isEqualTo(commitSha);
                });

        var claimedGit = gitSync.claimNext().orElseThrow();
        gitSync.handleFailure(claimedGit, "temporary network error", true);
        assertThat(outboxJobs.findByJobTypeAndReferenceId(OutboxJobType.GIT_SYNC, deliveryId))
                .get().satisfies(job -> {
                    assertThat(job.getStatus()).isEqualTo(OutboxJobStatus.PENDING);
                    assertThat(job.getAttemptCount()).isEqualTo(1);
                });
        assertThat(gitOperations.findByDeliveryId(deliveryId)).get()
                .extracting(operation -> operation.getStatus())
                .isEqualTo(com.example.agentcollab.domain.GitOperationStatus.PENDING);

        assertThat(gitSyncWorker.processGitNext()).isTrue();
        var gitOperation = gitOperations.findByDeliveryId(deliveryId).orElseThrow();
        assertThat(gitOperation.getStatus()).isEqualTo(com.example.agentcollab.domain.GitOperationStatus.SUCCEEDED);
        assertThat(gitOperation.getVerifiedCommitSha()).isEqualTo(commitSha);
        assertThat(gitOperation.getVerifiedAt()).isNotNull();
        var ciRun = ciRuns.findByDeliveryIdAndCommitSha(deliveryId, commitSha).orElseThrow();
        assertThat(ciRun.getCommitSha()).isEqualTo(saved.getCommitSha());
        assertThat(ciRun.getExternalId()).isNull();
        assertThat(tasks.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(com.example.agentcollab.domain.TaskStatus.CI_RUNNING);
        assertThat(workflows.findById(workflowId).orElseThrow().getStatus()).isEqualTo(com.example.agentcollab.domain.WorkflowStatus.CI_RUNNING);

        String ciExternalId = "mock-ci:" + commitSha;
        var runningCi = ciSync.claimNext().orElseThrow();
        ciSync.complete(runningCi, new com.example.agentcollab.client.CiProviderClient.CiProviderResult(
                ciExternalId, commitSha, com.example.agentcollab.domain.CiRunStatus.RUNNING,
                null, "https://ci.example.invalid/runs/running", true, true));
        assertThat(ciRuns.findById(ciRun.getId()).orElseThrow().getStatus())
                .isEqualTo(com.example.agentcollab.domain.CiRunStatus.RUNNING);
        assertThat(outboxJobs.findByJobTypeAndReferenceId(OutboxJobType.CI_SYNC, ciRun.getId()))
                .get().satisfies(job -> {
                    assertThat(job.getStatus()).isEqualTo(OutboxJobStatus.PENDING);
                    assertThat(job.getAttemptCount()).isZero();
                });

        var claimedCi = ciSync.claimNext().orElseThrow();
        ciSync.complete(claimedCi, new com.example.agentcollab.client.CiProviderClient.CiProviderResult(
                ciExternalId, "abcdefabcdefabcdefabcdefabcdefabcdefabcd",
                com.example.agentcollab.domain.CiRunStatus.PASSED, "SUCCESS",
                "https://ci.example.invalid/runs/stale", true, true));
        assertThat(ciRuns.findById(ciRun.getId()).orElseThrow().getStatus())
                .isEqualTo(com.example.agentcollab.domain.CiRunStatus.UNKNOWN);
        assertThat(tasks.findById(task.getId()).orElseThrow().getStatus())
                .isEqualTo(com.example.agentcollab.domain.TaskStatus.CI_RUNNING);
        assertThat(workflows.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(com.example.agentcollab.domain.WorkflowStatus.CI_RUNNING);

        assertThat(gitSyncWorker.processCiNext()).isTrue();
        assertThat(ciRuns.findById(ciRun.getId()).orElseThrow().getStatus()).isEqualTo(com.example.agentcollab.domain.CiRunStatus.PASSED);
        assertThat(ciRuns.findById(ciRun.getId()).orElseThrow().getExternalId()).isEqualTo(ciExternalId);
        assertThat(taskDeliveries.findById(deliveryId).orElseThrow().getStatus()).isEqualTo(com.example.agentcollab.domain.TaskDeliveryStatus.PASSED);
        assertThat(workflows.findById(workflowId).orElseThrow().getStatus()).isEqualTo(com.example.agentcollab.domain.WorkflowStatus.READY_TO_CLOSE);
        assertThat(tasks.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(com.example.agentcollab.domain.TaskStatus.DONE);
        assertThat(ciRuns.findById(ciRun.getId()).orElseThrow().getConfigurationPresent()).isTrue();
        assertThat(ciRuns.findById(ciRun.getId()).orElseThrow().getConfigurationRecognized()).isTrue();

        mvc.perform(get("/api/tasks/{id}/git-operations", task.getId())
                        .header("Authorization", bearer(reviewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deliveryId").value(deliveryId))
                .andExpect(jsonPath("$[0].commitSha").value(commitSha))
                .andExpect(jsonPath("$[0].verifiedCommitSha").value(commitSha))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"));
        mvc.perform(get("/api/tasks/{id}/ci-runs", task.getId())
                        .header("Authorization", bearer(reviewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deliveryId").value(deliveryId))
                .andExpect(jsonPath("$[0].commitSha").value(commitSha))
                .andExpect(jsonPath("$[0].configurationRecognized").value(true));

        mvc.perform(post("/api/workflows/{id}/close", workflowId)
                        .header("Authorization", bearer(reviewerToken)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/workflows/{id}/close", workflowId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
        assertThat(projects.findById(projectId).orElseThrow().getCiStatus())
                .isEqualTo(com.example.agentcollab.domain.ProjectCiStatus.CI_REQUIRED);

        mvc.perform(post("/api/tasks/{id}/delivery", task.getId())
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(deliveryId));
        assertThat(taskDeliveries.count()).isEqualTo(1);
        assertThat(outboxJobs.findByJobTypeAndReferenceId(OutboxJobType.GIT_SYNC, deliveryId)).isPresent();

        mvc.perform(get("/api/tasks/{id}/deliveries", task.getId())
                        .header("Authorization", bearer(reviewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(deliveryId));
    }

    @Test
    void staleCodeContextPreventsTaskPackageConfirmation() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "stale-package-context");
        updateProfile(leaderToken, projectId, 13);
        long workflowId = createCiBootstrap(leaderToken, projectId, "stale package initialization");
        advanceToBuildPlan(leaderToken, workflowId);
        confirm(leaderToken, workflowId, "approve-plan", 1).andExpect(status().isOk());
        mvc.perform(post("/api/workflows/{id}/create-tasks", workflowId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk());

        Task task = tasks.findByWorkflowIdOrderById(workflowId).get(0);
        TaskPackage taskPackage = taskPackages.findByTaskIdAndStatus(task.getId(), TaskPackageStatus.CURRENT)
                .orElseThrow();
        CodeContextVersion context = codeContexts.findById(taskPackage.getCodeContextVersionId()).orElseThrow();
        context.markStale();
        codeContexts.save(context);

        mvc.perform(post("/api/tasks/{id}/packages/{version}/confirm", task.getId(), 1)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "packageId", taskPackage.getId(), "contentHash", taskPackage.getContentHash()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_PACKAGE_CONTEXT_STALE"));
        assertThat(tasks.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.ASSIGNED);
        assertThat(packageConfirmations.count()).isZero();
    }

    @Test
    void rejectsGitValidationThatReportsADifferentCommitSha() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "git-sha-binding");
        updateProfile(leaderToken, projectId, 13);
        long workflowId = createCiBootstrap(leaderToken, projectId, "bind initialization git sha");
        advanceToBuildPlan(leaderToken, workflowId);
        confirm(leaderToken, workflowId, "approve-plan", 1).andExpect(status().isOk());
        mvc.perform(post("/api/workflows/{id}/create-tasks", workflowId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk());

        Task task = tasks.findByWorkflowIdOrderById(workflowId).get(0);
        TaskPackage taskPackage = taskPackages.findByTaskIdAndStatus(task.getId(), TaskPackageStatus.CURRENT)
                .orElseThrow();
        mvc.perform(post("/api/tasks/{id}/packages/{version}/confirm", task.getId(), 1)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "packageId", taskPackage.getId(), "contentHash", taskPackage.getContentHash()))))
                .andExpect(status().isOk());

        String commitSha = "0123456789abcdef0123456789abcdef01234567";
        var report = finalReport(task, taskPackage, commitSha);
        String response = mvc.perform(post("/api/tasks/{id}/delivery", task.getId())
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(deliveryRequest(
                                taskPackage, report, task.getBranchName(), commitSha))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        long deliveryId = json.readTree(response).path("id").asLong();

        var claimed = gitSync.claimNext().orElseThrow();
        gitSync.complete(claimed, new com.example.agentcollab.client.GitProviderClient.GitValidationResult(
                true, true, true, true,
                "abcdefabcdefabcdefabcdefabcdefabcdefabcd", null, "git-fact-1", null));

        assertThat(gitOperations.findByDeliveryId(deliveryId)).get()
                .satisfies(operation -> {
                    assertThat(operation.getStatus()).isEqualTo(GitOperationStatus.FAILED);
                    assertThat(operation.getVerifiedCommitSha()).isNull();
                });
        assertThat(taskDeliveries.findById(deliveryId).orElseThrow().getStatus())
                .isEqualTo(TaskDeliveryStatus.REJECTED);
        assertThat(tasks.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(ciRuns.count()).isZero();
        assertThat(outboxJobs.findByJobTypeAndReferenceId(OutboxJobType.GIT_SYNC, deliveryId))
                .get().extracting(OutboxJob::getStatus).isEqualTo(OutboxJobStatus.FAILED);
    }

    @Test
    void rejectsInvalidPlanAndPlanWithOutdatedMemberProfile() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "core");
        updateProfile(leaderToken, projectId, 13);
        long workflowId = createWorkflow(leaderToken, projectId, "stale plan", "CHANGE", null);
        requestRun(leaderToken, workflowId, "generate-build-plan");
        assertThat(worker.processNext()).isTrue();

        var generated = documents.findByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(
                workflowId, DocumentType.BUILD_PLAN).get(0);
        var invalid = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(generated.getContent());
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid.path("tasks").get(0))
                .remove("acceptanceCriteria");
        mvc.perform(put("/api/workflows/{id}/plan-drafts", workflowId)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("content", invalid.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BUILD_PLAN"));

        var falseWorkload = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(generated.getContent());
        ((com.fasterxml.jackson.databind.node.ObjectNode) falseWorkload.path("assignments").get(0)
                .path("workloadSnapshot")).put("openEffortPoints", 7);
        mvc.perform(put("/api/workflows/{id}/plan-drafts", workflowId)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("content", falseWorkload.toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNEE_WORKLOAD_STALE"));

        updateProfile(leaderToken, projectId, 21);
        confirm(leaderToken, workflowId, "approve-plan", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNEE_PROFILE_STALE"));
        requestRun(leaderToken, workflowId, "generate-build-plan");
        assertThat(worker.processNext()).isTrue();
        confirm(leaderToken, workflowId, "approve-plan", 2).andExpect(status().isOk());
        mvc.perform(post("/api/workflows/{id}/create-tasks", workflowId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CI_BOOTSTRAP_REQUIRED"));
    }

    @Test
    void architecturePlanCreatesOnlyChildIntentsAndNoDevelopmentAssignments() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "architecture");
        long workflowId = createWorkflow(leaderToken, projectId, "platform architecture", "ARCHITECTURE", null);
        advanceToBuildPlan(leaderToken, workflowId);

        var generated = documents.findByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(
                workflowId, DocumentType.BUILD_PLAN).get(0);
        var edited = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(generated.getContent());
        edited.withArray("childIntents").addObject()
                .put("title", "Authentication feature")
                .put("description", "Implement project authentication")
                .put("intentLevel", "FEATURE");
        mvc.perform(put("/api/workflows/{id}/plan-drafts", workflowId)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("content", edited.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionNo").value(2));
        confirm(leaderToken, workflowId, "approve-plan", 2).andExpect(status().isOk());
        mvc.perform(post("/api/workflows/{id}/create-tasks", workflowId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskCount").value(0))
                .andExpect(jsonPath("$.childIntentCount").value(1))
                .andExpect(jsonPath("$.workflowStatus").value("READY_TO_CLOSE"));
        mvc.perform(post("/api/workflows/{id}/create-tasks", workflowId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.childIntentCount").value(1));
        assertThat(tasks.count()).isZero();
        assertThat(taskAssignments.count()).isZero();
        var children = workflows.findByParentWorkflowIdOrderById(workflowId);
        assertThat(children).singleElement().satisfies(child -> {
            assertThat(child.getIntentLevel().name()).isEqualTo("FEATURE");
            assertThat(child.getProjectId()).isEqualTo(projectId);
        });
    }

    private org.springframework.test.web.servlet.ResultActions confirm(
            String token, long workflowId, String action, int version) throws Exception {
        return mvc.perform(post("/api/workflows/{id}/{action}", workflowId, action)
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"versionNo\":" + version + "}"));
    }

    private String initialize(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/initialize").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", TEST_PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", TEST_PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private long createUser(String token, String username) throws Exception {
        String body = mvc.perform(post("/api/users").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", TEST_PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("userId").asLong();
    }

    private long createProject(String token, String name) throws Exception {
        String body = mvc.perform(post("/api/projects").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "name", name, "repositoryUrl", "https://github.com/example/" + name,
                                "defaultBranch", "main"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void addMember(String token, long projectId, long userId) throws Exception {
        mvc.perform(post("/api/projects/{id}/members", projectId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":" + userId + "}"))
                .andExpect(status().isCreated());
    }

    private long createWorkflow(String token, long projectId, String title) throws Exception {
        return createWorkflow(token, projectId, title, "FEATURE", null);
    }

    private long createWorkflow(String token, long projectId, String title,
                                 String intentLevel, Long parentWorkflowId) throws Exception {
        var request = new java.util.LinkedHashMap<String, Object>();
        request.put("title", title);
        request.put("description", "workflow description");
        request.put("intentLevel", intentLevel);
        if (parentWorkflowId != null) request.put("parentWorkflowId", parentWorkflowId);
        String body = mvc.perform(post("/api/projects/{id}/workflows", projectId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long createCiBootstrap(String token, long projectId, String title) throws Exception {
        String body = mvc.perform(post("/api/projects/{id}/ci-bootstrap", projectId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "title", title,
                                "description", "initialize project scaffold, build entry points and CI"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intentLevel").value("FEATURE"))
                .andExpect(jsonPath("$.completionMode").value("CI_BOOTSTRAP"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long requestRun(String token, long workflowId, String action) throws Exception {
        ensureCurrentCodeContext(workflowId);
        String body = mvc.perform(post("/api/workflows/{id}/{action}", workflowId, action)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.statusUrl").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("runId").asLong();
    }

    private void requestRunExpecting(
            String token, long workflowId, String action,
            org.springframework.test.web.servlet.ResultMatcher expectedStatus) throws Exception {
        ensureCurrentCodeContext(workflowId);
        mvc.perform(post("/api/workflows/{id}/{action}", workflowId, action)
                        .header("Authorization", bearer(token)))
                .andExpect(expectedStatus);
    }

    private void updateProfile(String token, long projectId, int weeklyCapacityPoints) throws Exception {
        mvc.perform(put("/api/projects/{id}/members/me/profile", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "summary", "backend lead",
                                "responsibilities", java.util.List.of("API"),
                                "skills", java.util.List.of("Java", "PostgreSQL"),
                                "experience", java.util.List.of("REST"),
                                "preferredTaskTypes", java.util.List.of("backend"),
                                "limitations", java.util.List.of(),
                                "availability", "PART_TIME",
                                "weeklyCapacityPoints", weeklyCapacityPoints,
                                "notes", "test profile"))))
                .andExpect(status().isOk());
    }

    private com.fasterxml.jackson.databind.node.ObjectNode finalReport(
            com.example.agentcollab.domain.Task task,
            com.example.agentcollab.domain.TaskPackage taskPackage,
            String commitSha) {
        var report = json.createObjectNode();
        report.put("schemaVersion", "1.0");
        report.put("taskId", task.getExternalKey());
        report.put("packageId", taskPackage.getId());
        report.put("packageVersion", taskPackage.getPackageVersion());
        report.put("packageHash", taskPackage.getContentHash());
        report.put("codeContextVersionId", taskPackage.getCodeContextVersionId());
        report.put("contextPlanId", taskPackage.getContextPlanId());
        report.put("baseCommitSha", taskPackage.getBaseCommit());
        report.put("outcome", "READY_FOR_REVIEW");
        report.put("summary", "Implemented and locally verified the assigned task.");
        report.putArray("changedFiles").add("src/main/java/example/Delivery.java");
        report.putArray("tests").addObject()
                .put("command", "mvn test")
                .put("status", "PASSED")
                .put("summary", "Local tests passed");
        report.putObject("git")
                .put("branchName", task.getBranchName())
                .put("commitSha", commitSha)
                .putNull("pullRequestUrl");
        report.putArray("acceptanceCriteria").addObject()
                .put("criterion", "Delivery is recorded")
                .put("status", "PASSED")
                .put("evidence", "Local test output");
        report.putArray("unresolvedIssues");
        report.putArray("outOfScopeChanges");
        report.putArray("blockers");
        return report;
    }

    private Map<String, Object> deliveryRequest(
            com.example.agentcollab.domain.TaskPackage taskPackage,
            com.fasterxml.jackson.databind.JsonNode report,
            String branchName,
            String commitSha) {
        var request = new java.util.LinkedHashMap<String, Object>();
        request.put("packageId", taskPackage.getId());
        request.put("packageVersion", taskPackage.getPackageVersion());
        request.put("packageHash", taskPackage.getContentHash());
        request.put("finalReport", report);
        request.put("branchName", branchName);
        request.put("commitSha", commitSha);
        request.put("pullRequestUrl", null);
        return request;
    }

    private void advanceToBuildPlan(String token, long workflowId) throws Exception {
        requestRun(token, workflowId, "generate-design");
        assertThat(worker.processNext()).isTrue();
        confirm(token, workflowId, "confirm-design", 1).andExpect(status().isOk());
        requestRun(token, workflowId, "generate-spec");
        assertThat(worker.processNext()).isTrue();
        confirm(token, workflowId, "confirm-spec", 1).andExpect(status().isOk());
        requestRun(token, workflowId, "generate-build-plan");
        assertThat(worker.processNext()).isTrue();
    }

    private String bearer(String token) { return "Bearer " + token; }

    private long createAgentRun(long workflowId, AgentRunType type) {
        long contextId = ensureCurrentCodeContext(workflowId);
        CodeContextVersion context = codeContexts.findById(contextId).orElseThrow();
        AgentRun run = new AgentRun(workflowId, type, "test", "test-model", "test run");
        run.bindGenerationContext(context.getInventoryVersionId(), context.getContextPlanId(), context.getId());
        run.start();
        return agentRuns.save(run).getId();
    }

    private long ensureCurrentCodeContext(long workflowId) {
        var current = contextPlans.findTopByWorkflowIdOrderByCreatedAtDesc(workflowId)
                .flatMap(plan -> codeContexts.findTopByContextPlanIdAndStatusOrderByCreatedAtDesc(
                        plan.getId(), CodeContextStatus.CURRENT));
        if (current.isPresent()) return current.get().getId();

        Workflow workflow = workflows.findById(workflowId).orElseThrow();
        Project project = projects.findById(workflow.getProjectId()).orElseThrow();
        RepoInventoryVersion inventory = inventories.findTopByProjectIdAndStatusOrderByCreatedAtDesc(
                project.getId(), RepoInventoryStatus.CURRENT).orElseGet(() -> inventories.save(
                new RepoInventoryVersion(project.getId(), project.getRepositoryUrl(), project.getDefaultBranch(),
                        "1111111111111111111111111111111111111111", json.createObjectNode(),
                        json.createObjectNode().put("fileCount", 0))));
        var planJson = json.createObjectNode();
        planJson.put("intentLevel", workflow.getIntentLevel().name());
        var targets = planJson.putObject("readTargets");
        targets.putArray("files");
        targets.putArray("directories");
        targets.putArray("searchQueries");
        planJson.putArray("expectedEvidence").add("test context");
        planJson.putArray("uncertainties");
        CodeContextPlan plan = contextPlans.save(new CodeContextPlan(
                project.getId(), workflowId, inventory.getId(), null, planJson));
        plan.markUsed();
        contextPlans.save(plan);
        var evidence = json.createObjectNode();
        evidence.put("baseCommitSha", inventory.getCommitSha());
        evidence.putArray("relatedFiles");
        CodeContextVersion context = codeContexts.save(new CodeContextVersion(project.getId(), inventory.getId(), plan.getId(),
                project.getRepositoryUrl(), inventory.getBranchName(), inventory.getCommitSha(),
                inventory.getRepositoryProfile(), evidence, null));
        codeContextFiles.save(new CodeContextFile(context.getId(), "src/main/java/example/App.java",
                "hash-app", CodeEvidenceType.SOURCE_FILE, "Existing application entry point",
                json.createArrayNode(), "package example; public class App {}"));
        return context.getId();
    }

    private void assertDocumentVersions(long workflowId, DocumentType type, Long... runIds) {
        var versions = documents.findByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(workflowId, type);
        assertThat(versions).extracting(value -> value.getVersionNo()).containsExactly(2, 1);
        assertThat(versions).extracting(value -> value.getAgentRunId()).containsExactly(runIds);
    }
}
