package com.example.agentcollab.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;

/** Shared transport/error handling for read-only GitHub REST adapters. */
abstract class GitHubApiClientSupport {
    protected final RestClient client;

    protected GitHubApiClientSupport(RestClient.Builder builder, String apiUrl, String token) {
        RestClient.Builder configured = builder.baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");
        if (token != null && !token.isBlank()) {
            configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        this.client = configured.build();
    }

    protected JsonNode get(URI uri) {
        try {
            JsonNode response = client.get().uri(uri).retrieve().body(JsonNode.class);
            if (response == null) {
                throw new ProviderSyncException("GitHub returned an empty response", true);
            }
            return response;
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            boolean retryable = status == 429 || status == 408 || ex.getStatusCode().is5xxServerError();
            String message = switch (status) {
                case 401 -> "GitHub authentication failed";
                case 403 -> "GitHub access forbidden or rate limited";
                case 404 -> "GitHub resource not found";
                case 429 -> "GitHub rate limit exceeded";
                default -> "GitHub request failed with HTTP " + status;
            };
            throw new GitHubHttpException(message, retryable, status);
        } catch (ResourceAccessException ex) {
            throw new ProviderSyncException("GitHub is temporarily unreachable", true);
        }
    }

    protected static GitHubGitProviderClient.RepositoryRef parseRepository(String repositoryUrl) {
        try {
            URI uri = URI.create(repositoryUrl);
            if (!"github.com".equalsIgnoreCase(uri.getHost())) throw new IllegalArgumentException();
            String path = uri.getPath();
            if (path.startsWith("/")) path = path.substring(1);
            if (path.endsWith(".git")) path = path.substring(0, path.length() - 4);
            String[] parts = path.split("/");
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) throw new IllegalArgumentException();
            return new GitHubGitProviderClient.RepositoryRef(parts[0], parts[1]);
        } catch (RuntimeException ex) {
            throw new ProviderSyncException("Unsupported GitHub repository URL", false);
        }
    }

    protected static String requiredText(JsonNode node, String field, String message) {
        String value = node.path(field).asText("");
        if (value.isBlank()) throw new ProviderSyncException(message, false);
        return value;
    }

    static final class GitHubHttpException extends ProviderSyncException {
        private final int status;
        GitHubHttpException(String message, boolean retryable, int status) {
            super(message, retryable); this.status = status;
        }
        int status() { return status; }
    }
}
