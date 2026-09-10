package com.example.agentcollab.client;

import com.example.agentcollab.domain.Project;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
@Profile("!test & !mock-provider")
public class GitCodeContextProvider implements CodeContextProvider {
    private final RestClient client;

    public GitCodeContextProvider(RestClient.Builder builder,
                                  @Value("${app.code-context.git.api-url:https://api.github.com}") String apiUrl,
                                  @Value("${app.code-context.git.token:}") String token) {
        RestClient.Builder configured = builder.baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");
        if (token != null && !token.isBlank()) configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        this.client = configured.build();
    }

    @Override
    public RepositoryHead readHead(Project project) {
        RepositoryRef repository = parseRepository(project.getRepositoryUrl());
        JsonNode response = get(uri("/repos/{owner}/{repo}/commits/{branch}", repository, project.getDefaultBranch()));
        String sha = requiredText(response, "sha", "Git Provider did not return a commit SHA");
        return new RepositoryHead(project.getDefaultBranch(), sha);
    }

    @Override
    public List<RepositoryFileFact> readTree(Project project, String commitSha) {
        RepositoryRef repository = parseRepository(project.getRepositoryUrl());
        JsonNode response = get(uri("/repos/{owner}/{repo}/git/trees/{ref}", repository, commitSha, "recursive", "1"));
        List<RepositoryFileFact> files = new ArrayList<>();
        for (JsonNode item : response.path("tree")) {
            if (!"blob".equals(item.path("type").asText())) continue;
            files.add(new RepositoryFileFact(
                    item.path("path").asText(), item.path("size").asLong(0), item.path("sha").asText(null)));
        }
        return List.copyOf(files);
    }

    @Override
    public List<RepositoryFileContent> readFiles(Project project, String commitSha, List<String> paths) {
        RepositoryRef repository = parseRepository(project.getRepositoryUrl());
        List<RepositoryFileContent> files = new ArrayList<>();
        for (String path : paths) {
            URI uri = UriComponentsBuilder.fromPath("/repos/{owner}/{repo}/contents")
                    .pathSegment(path.split("/"))
                    .queryParam("ref", commitSha)
                    .buildAndExpand(repository.owner(), repository.repository())
                    .encode().toUri();
            JsonNode response = get(uri);
            if (!"base64".equals(response.path("encoding").asText())) {
                throw new ProviderSyncException("Git Provider returned an unsupported file encoding", false);
            }
            try {
                String content = new String(Base64.getMimeDecoder().decode(response.path("content").asText()),
                        StandardCharsets.UTF_8);
                files.add(new RepositoryFileContent(path, response.path("sha").asText(null), content));
            } catch (IllegalArgumentException ex) {
                throw new ProviderSyncException("Git Provider returned invalid file content", false);
            }
        }
        return List.copyOf(files);
    }

    private URI uri(String path, RepositoryRef repository, String value) {
        return UriComponentsBuilder.fromPath(path)
                .buildAndExpand(repository.owner(), repository.repository(), value).encode().toUri();
    }

    private URI uri(String path, RepositoryRef repository, String value, String queryName, String queryValue) {
        return UriComponentsBuilder.fromPath(path).queryParam(queryName, queryValue)
                .buildAndExpand(repository.owner(), repository.repository(), value).encode().toUri();
    }

    private JsonNode get(URI uri) {
        try {
            JsonNode response = client.get().uri(uri).retrieve().body(JsonNode.class);
            if (response == null) throw new ProviderSyncException("Git Provider returned an empty response", true);
            return response;
        } catch (RestClientResponseException ex) {
            boolean retryable = ex.getStatusCode().value() == 429 || ex.getStatusCode().is5xxServerError();
            throw new ProviderSyncException("Git Provider request failed with HTTP " + ex.getStatusCode().value(), retryable);
        } catch (ResourceAccessException ex) {
            throw new ProviderSyncException("Git Provider is temporarily unreachable", true);
        }
    }

    private String requiredText(JsonNode node, String field, String message) {
        String value = node.path(field).asText();
        if (value.isBlank()) throw new ProviderSyncException(message, false);
        return value;
    }

    static RepositoryRef parseRepository(String repositoryUrl) {
        try {
            URI uri = URI.create(repositoryUrl);
            if (!"github.com".equalsIgnoreCase(uri.getHost())) {
                throw new IllegalArgumentException("Only github.com repositories are supported");
            }
            String path = uri.getPath();
            if (path.startsWith("/")) path = path.substring(1);
            if (path.endsWith(".git")) path = path.substring(0, path.length() - 4);
            String[] parts = path.split("/");
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalArgumentException("Repository URL must contain owner and repository");
            }
            return new RepositoryRef(parts[0], parts[1]);
        } catch (RuntimeException ex) {
            throw new ProviderSyncException("Unsupported Git repository URL", false);
        }
    }

    record RepositoryRef(String owner, String repository) {}
}
