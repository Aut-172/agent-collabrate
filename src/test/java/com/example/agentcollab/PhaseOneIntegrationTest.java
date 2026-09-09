package com.example.agentcollab;

import com.example.agentcollab.repository.MemberProfileVersionRepository;
import com.example.agentcollab.repository.ProjectMemberRepository;
import com.example.agentcollab.repository.ProjectRepository;
import com.example.agentcollab.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PhaseOneIntegrationTest {
    static final String TEST_PASSWORD = "pw-" + java.util.UUID.randomUUID();
    static final String WRONG_PASSWORD = "pw-" + java.util.UUID.randomUUID();
    static final String DATABASE_PASSWORD = java.util.UUID.randomUUID().toString();
    static final String JWT_SECRET = java.util.UUID.randomUUID() + "-" + java.util.UUID.randomUUID();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agent_collab")
            .withUsername("test")
            .withPassword(DATABASE_PASSWORD);

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
    @Autowired MemberProfileVersionRepository profileVersions;
    @Autowired ProjectMemberRepository members;
    @Autowired ProjectRepository projects;
    @Autowired UserRepository users;

    @BeforeEach
    void clearDatabase() {
        profileVersions.deleteAll();
        members.deleteAll();
        projects.deleteAll();
        users.deleteAll();
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
    }

    @Test
    void initializationLoginAndBcryptWork() throws Exception {
        String token = initialize("leader", TEST_PASSWORD);

        mvc.perform(get("/api/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("leader"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("username", "leader", "password", WRONG_PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/auth/initialize").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("username", "other", "password", TEST_PASSWORD))))
                .andExpect(status().isConflict());

        assertThat(users.findByUsername("leader").orElseThrow().getPasswordHash())
                .startsWith("$2").doesNotContain(TEST_PASSWORD);
    }

    @Test
    void leaderAndMemberProfilesAreProjectScopedAndVersioned() throws Exception {
        String leaderToken = initialize("leader", TEST_PASSWORD);
        long projectId = createProject(leaderToken, "core");

        mvc.perform(get("/api/projects/{id}/members/me/profile", projectId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectRole").value("LEADER"))
                .andExpect(jsonPath("$.profileCompleted").value(false));

        mvc.perform(put("/api/projects/{id}/members/me/profile", projectId)
                        .header("Authorization", bearer(leaderToken))
                        .contentType(MediaType.APPLICATION_JSON).content(profile("backend lead")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileVersion").value(1))
                .andExpect(jsonPath("$.capabilityProfile.skills[0]").value("Java"));
        mvc.perform(put("/api/projects/{id}/members/me/profile", projectId)
                        .header("Authorization", bearer(leaderToken))
                        .contentType(MediaType.APPLICATION_JSON).content(profile("security lead")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileVersion").value(2));
        mvc.perform(put("/api/projects/{id}/members/me/profile", projectId)
                        .header("Authorization", bearer(leaderToken))
                        .contentType(MediaType.APPLICATION_JSON).content(oversizedProfile()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CAPABILITY_PROFILE"));

        var leaderMember = members.findByProjectIdAndUserId(projectId, users.findByUsername("leader").orElseThrow().getId()).orElseThrow();
        assertThat(profileVersions.countByProjectMemberId(leaderMember.getId())).isEqualTo(2);
        assertThat(users.findByUsername("leader").orElseThrow().getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName).doesNotContain("capabilityProfile", "projectRole");
    }

    @Test
    void onlyProjectLeaderCanAddMemberAndNonMembersCannotReadProject() throws Exception {
        String leaderToken = initialize("leader", TEST_PASSWORD);
        long projectId = createProject(leaderToken, "core");
        long memberId = createUser(leaderToken, "member");
        long outsiderId = createUser(leaderToken, "outsider");

        mvc.perform(post("/api/projects/{id}/members", projectId)
                        .header("Authorization", bearer(leaderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + memberId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectRole").value("MEMBER"))
                .andExpect(jsonPath("$.profileCompleted").value(false));

        String memberToken = login("member", TEST_PASSWORD);
        mvc.perform(put("/api/projects/{id}/members/me/profile", projectId)
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON).content(profile("member backend")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileCompleted").value(true))
                .andExpect(jsonPath("$.profileVersion").value(1));
        mvc.perform(post("/api/projects/{id}/members", projectId)
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + outsiderId + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEADER_REQUIRED"));

        String outsiderToken = login("outsider", TEST_PASSWORD);
        mvc.perform(get("/api/projects/{id}", projectId).header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/projects/{id}/members/{userId}", projectId, memberId)
                        .header("Authorization", bearer(leaderToken)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/projects/{id}", projectId).header("Authorization", bearer(memberToken)))
                .andExpect(status().isNotFound());
    }

    private String initialize(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/initialize").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("username", username, "password", password))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private long createProject(String token, String name) throws Exception {
        String body = mvc.perform(post("/api/projects").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of(
                                "name", name, "repositoryUrl", "https://github.com/example/" + name,
                                "defaultBranch", "main"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.gitProvider").value("github"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long createUser(String token, String username) throws Exception {
        String body = mvc.perform(post("/api/users").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of(
                                "username", username, "password", TEST_PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("userId").asLong();
    }

    private String profile(String summary) throws Exception {
        return json.writeValueAsString(java.util.Map.of(
                "summary", summary,
                "responsibilities", java.util.List.of("API"),
                "skills", java.util.List.of("Java", "PostgreSQL"),
                "experience", java.util.List.of("REST"),
                "preferredTaskTypes", java.util.List.of("backend"),
                "limitations", java.util.List.of(),
                "availability", "PART_TIME",
                "notes", "test profile"));
    }

    private String oversizedProfile() throws Exception {
        JsonNode valid = json.readTree(profile("invalid oversized skills"));
        var skills = ((com.fasterxml.jackson.databind.node.ObjectNode) valid).putArray("skills");
        for (int i = 0; i < 101; i++) skills.add("skill-" + i);
        return json.writeValueAsString(valid);
    }

    private String bearer(String token) { return "Bearer " + token; }
}
