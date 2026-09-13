package com.example.agentcollab;

import com.example.agentcollab.client.MockCodeContextProvider;
import com.example.agentcollab.client.ProviderSyncException;
import com.example.agentcollab.domain.*;
import com.example.agentcollab.repository.*;
import com.example.agentcollab.service.RepoIngestionWorker;
import com.example.agentcollab.service.AgentRunWorker;
import com.example.agentcollab.service.CodeEvidenceService;
import com.example.agentcollab.service.CodeEvidenceCollector;
import com.example.agentcollab.service.CodeEvidenceWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class RepoInventoryIntegrationTest {
    static final String TEST_PASSWORD = "pw-" + java.util.UUID.randomUUID();
    static final String DATABASE_PASSWORD = java.util.UUID.randomUUID().toString();
    static final String JWT_SECRET = java.util.UUID.randomUUID() + "-" + java.util.UUID.randomUUID();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agent_collab").withUsername("test").withPassword(DATABASE_PASSWORD);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> JWT_SECRET);
        registry.add("app.jwt.access-token-expiration", () -> 3600000);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RepoIngestionWorker worker;
    @Autowired AgentRunWorker agentWorker;
    @Autowired CodeEvidenceWorker evidenceWorker;
    @Autowired CodeEvidenceService evidenceService;
    @Autowired CodeEvidenceCollector evidenceCollector;
    @Autowired MockCodeContextProvider provider;
    @Autowired OutboxJobRepository jobs;
    @Autowired RepoInventoryFileRepository inventoryFiles;
    @Autowired RepoInventoryVersionRepository inventories;
    @Autowired CodeContextRunRepository contextRuns;
    @Autowired ProjectMemberRepository members;
    @Autowired ProjectRepository projects;
    @Autowired UserRepository users;
    @Autowired AgentRunRepository agentRuns;
    @Autowired CodeContextPlanRepository contextPlans;
    @Autowired CodeContextVersionRepository contexts;
    @Autowired CodeContextFileRepository evidenceFiles;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearDatabase() {
        jdbc.execute("TRUNCATE TABLE outbox_jobs, users RESTART IDENTITY CASCADE");
        provider.reset();
    }

    @Test
    void leaderCanSynchronizeTraceableRepositoryInventory() throws Exception {
        String token = initialize("leader");
        long projectId = createProject(token, "inventory");

        long runId = requestSync(token, projectId);
        mvc.perform(post("/api/projects/{id}/code-context/sync", projectId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.runId").value(runId));
        assertThat(jobs.findByJobTypeAndReferenceId(OutboxJobType.CODE_CONTEXT_SYNC, runId))
                .get().extracting(OutboxJob::getStatus).isEqualTo(OutboxJobStatus.PENDING);

        assertThat(worker.processNext()).isTrue();

        mvc.perform(get("/api/projects/{id}/code-context/runs/{runId}", projectId, runId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.inventoryVersionId").isNumber());
        mvc.perform(get("/api/projects/{id}/repo-inventory/latest", projectId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("GIT"))
                .andExpect(jsonPath("$.branchName").value("main"))
                .andExpect(jsonPath("$.commitSha").value("1111111111111111111111111111111111111111"))
                .andExpect(jsonPath("$.repositoryProfile.buildTools[0]").value("Maven"))
                .andExpect(jsonPath("$.treeSummary.fileCount").value(6));

        var inventory = inventories.findTopByProjectIdAndStatusOrderByCreatedAtDesc(
                projectId, RepoInventoryStatus.CURRENT).orElseThrow();
        var files = inventoryFiles.findByInventoryVersionIdOrderByPath(inventory.getId());
        assertThat(files).extracting(RepoInventoryFile::getPath).doesNotContain(".env");
        assertThat(files).filteredOn(file -> file.getFileType() == RepoFileType.BINARY)
                .allMatch(file -> file.getIndexedSummary() == null);
        assertThat(files).filteredOn(file -> file.getSizeBytes() > 200000)
                .allMatch(file -> file.getIndexedSummary() == null);
        assertThat(provider.getLastRequestedPaths())
                .contains("README.md", "pom.xml", "src/main/java/example/App.java")
                .doesNotContain(".env", "assets/logo.png", "docs/large.md");
        assertThat(projects.findById(projectId).orElseThrow().getLatestContextCommit())
                .isEqualTo("1111111111111111111111111111111111111111");
    }

    @Test
    void newDefaultBranchCommitMarksPreviousInventoryStale() throws Exception {
        String token = initialize("leader");
        long projectId = createProject(token, "stale");
        long firstRun = requestSync(token, projectId);
        assertThat(worker.processNext()).isTrue();
        Long firstInventory = contextRuns.findById(firstRun).orElseThrow().getInventoryVersionId();

        provider.useCommit("2222222222222222222222222222222222222222");
        long secondRun = requestSync(token, projectId);
        assertThat(worker.processNext()).isTrue();

        assertThat(inventories.findById(firstInventory).orElseThrow().getStatus())
                .isEqualTo(RepoInventoryStatus.STALE);
        assertThat(contextRuns.findById(secondRun).orElseThrow().getInventoryVersionId())
                .isNotEqualTo(firstInventory);
        assertThat(projects.findById(projectId).orElseThrow().getLatestContextCommit())
                .isEqualTo("2222222222222222222222222222222222222222");
    }

    @Test
    void workflowCreationQueuesFreshRepositoryInventoryRefresh() throws Exception {
        String token = initialize("leader");
        long projectId = createProject(token, "workflow-refresh");
        long initialRun = requestSync(token, projectId);
        assertThat(worker.processNext()).isTrue();
        Long initialInventory = contextRuns.findById(initialRun).orElseThrow().getInventoryVersionId();

        provider.useCommit("2222222222222222222222222222222222222222");
        createWorkflow(token, projectId);

        assertThat(inventories.findById(initialInventory).orElseThrow().getStatus())
                .isEqualTo(RepoInventoryStatus.STALE);
        assertThat(worker.processNext()).isTrue();
        assertThat(inventories.findTopByProjectIdAndStatusOrderByCreatedAtDesc(
                projectId, RepoInventoryStatus.CURRENT).orElseThrow().getCommitSha())
                .isEqualTo("2222222222222222222222222222222222222222");
    }

    @Test
    void providerFailureIsVisibleAndDoesNotCreateInventory() throws Exception {
        String token = initialize("leader");
        long projectId = createProject(token, "failure");
        provider.failWith("repository denied", false);
        long runId = requestSync(token, projectId);

        assertThat(worker.processNext()).isTrue();

        mvc.perform(get("/api/projects/{id}/code-context/runs/{runId}", projectId, runId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("repository denied"));
        assertThat(inventories.count()).isZero();
        mvc.perform(get("/api/projects/{id}/repo-inventory/latest", projectId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPO_INVENTORY_NOT_FOUND"));
    }

    @Test
    void everyProjectMemberCanRequestSynchronization() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "permission");
        long memberId = createUser(leaderToken, "member");
        mvc.perform(post("/api/projects/{id}/members", projectId)
                        .header("Authorization", bearer(leaderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", memberId))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/projects/{id}/code-context/sync", projectId)
                        .header("Authorization", bearer(login("member"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").isNumber());
    }

    @Test
    void platformAgentPlansContextAndOrchestratorCollectsBoundedEvidence() throws Exception {
        String token = initialize("leader");
        long projectId = createProject(token, "planning");
        requestSync(token, projectId);
        assertThat(worker.processNext()).isTrue();
        long workflowId = createWorkflow(token, projectId);
        assertThat(worker.processNext()).isTrue();

        String response = mvc.perform(post("/api/workflows/{id}/code-context/refresh", workflowId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        long agentRunId = json.readTree(response).path("runId").asLong();
        assertThat(agentWorker.processNext()).isTrue();
        var planRun = agentRuns.findById(agentRunId).orElseThrow();
        assertThat(planRun.getContextPlanId()).isNotNull();
        assertThat(planRun.getCodeContextVersionId()).isNull();
        assertThat(evidenceWorker.processNext()).isTrue();

        mvc.perform(get("/api/workflows/{id}/code-context", workflowId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contextPlanId").value(planRun.getContextPlanId()))
                .andExpect(jsonPath("$.baseCommitSha").value("1111111111111111111111111111111111111111"))
                .andExpect(jsonPath("$.evidence.roundsUsed").value(1))
                .andExpect(jsonPath("$.files.length()").value(4));
        var completedRun = agentRuns.findById(agentRunId).orElseThrow();
        assertThat(completedRun.getCodeContextVersionId()).isNotNull();
        assertThat(contextPlans.findById(completedRun.getContextPlanId()).orElseThrow().getStatus())
                .isEqualTo(CodeContextPlanStatus.USED);
        assertThat(contexts.findById(completedRun.getCodeContextVersionId())).isPresent();
        assertThat(evidenceFiles.findByContextVersionIdOrderByPath(completedRun.getCodeContextVersionId()))
                .hasSize(4);
        assertThat(provider.getLastRequestedPaths()).hasSizeLessThanOrEqualTo(20)
                .doesNotContain(".env", "assets/logo.png", "docs/large.md");

        provider.useCommit("2222222222222222222222222222222222222222");
        requestSync(token, projectId);
        assertThat(worker.processNext()).isTrue();
        assertThat(contexts.findById(completedRun.getCodeContextVersionId()).orElseThrow().getStatus())
                .isEqualTo(CodeContextStatus.STALE);
        mvc.perform(get("/api/workflows/{id}/code-context", workflowId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CODE_CONTEXT_NOT_FOUND"));
    }

    @Test
    void timedOutEvidenceCollectionIsRecoveredAndRetried() throws Exception {
        String token = initialize("leader");
        long projectId = createProject(token, "evidence-timeout");
        requestSync(token, projectId);
        assertThat(worker.processNext()).isTrue();
        long workflowId = createWorkflow(token, projectId);
        assertThat(worker.processNext()).isTrue();
        mvc.perform(post("/api/workflows/{id}/code-context/refresh", workflowId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isAccepted());
        assertThat(agentWorker.processNext()).isTrue();

        var claimed = evidenceService.claimNext().orElseThrow();
        jdbc.update("UPDATE outbox_jobs SET locked_at = now() - interval '10 minutes' WHERE id = ?",
                claimed.jobId());
        assertThat(evidenceService.recoverTimedOut()).isOne();
        assertThat(jobs.findById(claimed.jobId()).orElseThrow().getStatus()).isEqualTo(OutboxJobStatus.PENDING);
        assertThat(contextRuns.findById(claimed.runId()).orElseThrow().getStatus())
                .isEqualTo(CodeContextRunStatus.RUNNING);

        assertThat(evidenceWorker.processNext()).isTrue();
        assertThat(contextRuns.findById(claimed.runId()).orElseThrow().getStatus())
                .isEqualTo(CodeContextRunStatus.SUCCEEDED);
    }

    @Test
    void staleInventoryCannotBePersistedAfterProviderEvidenceWasRead() throws Exception {
        String token = initialize("leader");
        long projectId = createProject(token, "evidence-race");
        requestSync(token, projectId);
        assertThat(worker.processNext()).isTrue();
        long workflowId = createWorkflow(token, projectId);
        assertThat(worker.processNext()).isTrue();
        mvc.perform(post("/api/workflows/{id}/code-context/refresh", workflowId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isAccepted());
        assertThat(agentWorker.processNext()).isTrue();

        var claimed = evidenceService.claimNext().orElseThrow();
        var evidence = evidenceCollector.collect(evidenceService.context(claimed));
        provider.useCommit("2222222222222222222222222222222222222222");
        requestSync(token, projectId);
        assertThat(worker.processNext()).isTrue();

        assertThatThrownBy(() -> evidenceService.complete(claimed, evidence))
                .isInstanceOf(ProviderSyncException.class)
                .hasMessage("Repo Inventory became stale");
        evidenceService.handleFailure(claimed, "Repo Inventory became stale", false);
        assertThat(contexts.count()).isZero();
        assertThat(contextRuns.findById(claimed.runId()).orElseThrow().getStatus())
                .isEqualTo(CodeContextRunStatus.FAILED);
    }

    private long requestSync(String token, long projectId) throws Exception {
        String body = mvc.perform(post("/api/projects/{id}/code-context/sync", projectId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("runId").asLong();
    }

    private String initialize(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/initialize").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", TEST_PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("accessToken").asText();
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", TEST_PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("accessToken").asText();
    }

    private long createProject(String token, String name) throws Exception {
        String body = mvc.perform(post("/api/projects").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "name", name, "repositoryUrl", "https://github.com/example/" + name,
                                "defaultBranch", "main"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("id").asLong();
    }

    private long createUser(String token, String username) throws Exception {
        String body = mvc.perform(post("/api/users").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", TEST_PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("userId").asLong();
    }

    private long createWorkflow(String token, long projectId) throws Exception {
        String body = mvc.perform(post("/api/projects/{id}/workflows", projectId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "context planning",
                                "description", "collect repository evidence", "intentLevel", "FEATURE",
                                "pullRequestRequired", false))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("id").asLong();
    }

    private String bearer(String token) { return "Bearer " + token; }
}
