package com.example.agentcollab.client;

import com.example.agentcollab.domain.CiRun;
import com.example.agentcollab.domain.CiRunStatus;
import com.example.agentcollab.domain.Project;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Iterator;

@Component
@Profile("!test & !mock-provider")
public class GitHubCiProviderClient extends GitHubApiClientSupport implements CiProviderClient {
    public GitHubCiProviderClient(org.springframework.web.client.RestClient.Builder builder,
                                  @Value("${app.git.api-url:https://api.github.com}") String apiUrl,
                                  @Value("${app.git.token:}") String token) {
        super(builder, apiUrl, token);
    }

    @Override
    public CiProviderResult sync(Project project, CiRun run) {
        GitHubGitProviderClient.RepositoryRef repository = parseRepository(project.getRepositoryUrl());
        JsonNode response = get(UriComponentsBuilder.fromPath("/repos/{owner}/{repo}/commits/{sha}/check-runs")
                .queryParam("per_page", 100)
                .buildAndExpand(repository.owner(), repository.repository(), run.getCommitSha()).encode().toUri());
        String returnedSha = response.path("sha").asText("");
        JsonNode checks = response.path("check_runs");
        int count = checks.isArray() ? checks.size() : 0;
        if (count == 0) {
            return new CiProviderResult("github:checks:" + run.getCommitSha(), returnedSha,
                    CiRunStatus.UNKNOWN, "NO_CHECK_RUNS", null, false, false);
        }
        boolean running = false;
        boolean failed = false;
        String detailsUrl = null;
        String externalId = null;
        Iterator<JsonNode> iterator = checks.elements();
        while (iterator.hasNext()) {
            JsonNode check = iterator.next();
            if (returnedSha.isBlank()) returnedSha = check.path("head_sha").asText("");
            String status = check.path("status").asText("");
            String conclusion = check.path("conclusion").asText("");
            detailsUrl = detailsUrl == null ? check.path("html_url").asText(null) : detailsUrl;
            externalId = externalId == null ? check.path("id").asText(null) : externalId;
            if (!"completed".equalsIgnoreCase(status)) running = true;
            if ("completed".equalsIgnoreCase(status)
                    && !SetOfSuccess.contains(conclusion.toLowerCase())) failed = true;
        }
        CiRunStatus result = running ? CiRunStatus.RUNNING : failed ? CiRunStatus.FAILED : CiRunStatus.PASSED;
        String conclusion = running ? "IN_PROGRESS" : failed ? "FAILURE" : "SUCCESS";
        return new CiProviderResult(externalId, returnedSha, result, conclusion, detailsUrl, true, true);
    }

    private static final java.util.Set<String> SetOfSuccess = java.util.Set.of("success", "skipped", "neutral");
}
