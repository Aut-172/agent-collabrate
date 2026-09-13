package com.example.agentcollab.client;

import com.example.agentcollab.domain.Project;
import com.example.agentcollab.domain.TaskDelivery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;

@Component
@Profile("!test & !mock-provider")
public class GitHubGitProviderClient extends GitHubApiClientSupport implements GitProviderClient {
    public GitHubGitProviderClient(org.springframework.web.client.RestClient.Builder builder,
                                   @Value("${app.git.api-url:https://api.github.com}") String apiUrl,
                                   @Value("${app.git.token:}") String token) {
        super(builder, apiUrl, token);
    }

    @Override
    public GitValidationResult validate(Project project, TaskDelivery delivery) {
        RepositoryRef repository;
        try {
            repository = parseRepository(project.getRepositoryUrl());
        } catch (ProviderSyncException ex) {
            return invalid("Unsupported GitHub repository URL");
        }
        String commitSha = delivery.getCommitSha();
        JsonNode commit = getFact(path("/repos/{owner}/{repo}/commits/{sha}", repository, commitSha));
        if (commit == null) return invalid("Commit does not exist in the target repository");
        String verifiedSha = requiredText(commit, "sha", "GitHub did not return a commit SHA");
        boolean repositoryMatches = repository.fullName().equalsIgnoreCase(
                commit.path("repository").path("full_name").asText(repository.fullName()));

        boolean branchMatches = delivery.getBranchName() != null
                && !delivery.getBranchName().isBlank()
                && !delivery.getBranchName().equals(project.getDefaultBranch());
        String branchError = branchMatches ? null : "分支缺失或使用了默认分支";
        if (branchMatches) {
            JsonNode branch = getFact(path("/repos/{owner}/{repo}/branches/{branch}", repository, delivery.getBranchName()));
            String branchHeadSha = branch == null ? "" : branch.path("commit").path("sha").asText("");
            branchMatches = branch != null
                    && repositoryName(branch.path("name").asText(delivery.getBranchName()), delivery.getBranchName())
                    && commitSha.equalsIgnoreCase(branchHeadSha);
            if (!branchMatches) {
                branchError = branch == null || branchHeadSha.isBlank()
                        ? "任务分支不存在或没有 HEAD Commit"
                        : "任务分支 HEAD " + branchHeadSha + " 与交付 Commit " + commitSha + " 不一致";
            }
        }

        boolean pullRequestMatches = true;
        String pullRequestHeadSha = null;
        String externalId = "github:" + repository.owner() + "/" + repository.repository() + ":" + verifiedSha;
        if (delivery.getPullRequestUrl() != null && !delivery.getPullRequestUrl().isBlank()) {
            PullRequestRef pullRequest;
            try {
                pullRequest = parsePullRequest(delivery.getPullRequestUrl());
            } catch (ProviderSyncException ex) {
                return new GitValidationResult(true, true, branchMatches, false, verifiedSha,
                        null, externalId, "Pull Request URL is not a supported GitHub URL");
            }
            if (!repository.equals(pullRequest.repository())) {
                pullRequestMatches = false;
            } else {
                JsonNode pr = getFact(path("/repos/{owner}/{repo}/pulls/{number}", repository,
                        Integer.toString(pullRequest.number())));
                if (pr == null) {
                    pullRequestMatches = false;
                } else {
                    String fullName = pr.path("base").path("repo").path("full_name").asText("");
                    pullRequestHeadSha = pr.path("head").path("sha").asText(null);
                    pullRequestMatches = repository.fullName().equalsIgnoreCase(fullName)
                            && pullRequestHeadSha != null
                            && commitSha.equalsIgnoreCase(pullRequestHeadSha);
                }
                externalId = "github:pr:" + pullRequest.number();
            }
        }
        return new GitValidationResult(repositoryMatches, commitSha.equalsIgnoreCase(verifiedSha), branchMatches,
                pullRequestMatches, verifiedSha, pullRequestHeadSha, externalId,
                !repositoryMatches ? "Commit belongs to a different repository"
                        : !branchMatches ? branchError
                        : !pullRequestMatches ? "Pull Request repository or head SHA does not match delivery" : null);
    }

    private boolean repositoryName(String actual, String expected) { return actual.equalsIgnoreCase(expected); }

    private JsonNode getFact(URI uri) {
        try {
            return super.get(uri);
        } catch (GitHubHttpException ex) {
            if (ex.status() == 404) return null;
            throw ex;
        }
    }

    private GitValidationResult invalid(String message) {
        return new GitValidationResult(false, false, false, false, null, null, null, message);
    }

    private URI path(String template, RepositoryRef repository, String value) {
        return UriComponentsBuilder.fromPath(template).buildAndExpand(repository.owner(), repository.repository(), value)
                .encode().toUri();
    }

    private PullRequestRef parsePullRequest(String url) {
        try {
            URI uri = URI.create(url);
            if (!"github.com".equalsIgnoreCase(uri.getHost())) throw new IllegalArgumentException();
            String[] parts = uri.getPath().replaceFirst("^/", "").split("/");
            if (parts.length != 4 || !"pull".equals(parts[2])) throw new IllegalArgumentException();
            return new PullRequestRef(new RepositoryRef(parts[0], parts[1]), Integer.parseInt(parts[3]));
        } catch (RuntimeException ex) {
            throw new ProviderSyncException("Unsupported GitHub Pull Request URL", false);
        }
    }

    record RepositoryRef(String owner, String repository) {
        String fullName() { return owner + "/" + repository; }
    }
    private record PullRequestRef(RepositoryRef repository, int number) {}
}
