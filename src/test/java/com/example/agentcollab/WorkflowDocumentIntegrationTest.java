package com.example.agentcollab;

import com.example.agentcollab.domain.DocumentType;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.*;
import com.example.agentcollab.service.DocumentService;
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
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearDatabase() {
        documents.deleteAll();
        workflowMembers.deleteAll();
        jdbc.update("UPDATE workflows SET parent_workflow_id = NULL");
        workflows.deleteAll();
        profileVersions.deleteAll();
        projectMembers.deleteAll();
        projects.deleteAll();
        users.deleteAll();
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
                .andExpect(jsonPath("$.parentWorkflowId").value(architectureId));
        mvc.perform(get("/api/workflows/{id}", changeId).header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextAction").value("GENERATE_BUILD_PLAN"));
        assertThatThrownBy(() -> documentService.recordGeneratedDesign(changeId, "# Invalid design", 201L))
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
    void keepsDocumentVersionsAndRequiresCreatorToConfirmLatestVersion() throws Exception {
        String leaderToken = initialize("leader");
        long projectId = createProject(leaderToken, "core");
        long creatorId = createUser(leaderToken, "creator");
        addMember(leaderToken, projectId, creatorId);
        String creatorToken = login("creator");
        long workflowId = createWorkflow(creatorToken, projectId, "document flow");

        var designV1 = documentService.recordGeneratedDesign(workflowId, "# Design v1", 101L);
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

        documentService.recordGeneratedSpec(workflowId, "# Spec v1", 102L);
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

    private String bearer(String token) { return "Bearer " + token; }
}
