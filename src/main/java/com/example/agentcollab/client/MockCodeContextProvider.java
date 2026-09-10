package com.example.agentcollab.client;

import com.example.agentcollab.domain.Project;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
@Profile({"test", "mock-provider"})
public class MockCodeContextProvider implements CodeContextProvider {
    private String commitSha = "1111111111111111111111111111111111111111";
    private ProviderSyncException failure;
    private List<String> lastRequestedPaths = List.of();

    private final Map<String, String> content = Map.of(
            "README.md", "# Example\nA Spring Boot service.",
            "pom.xml", "<project><artifactId>example</artifactId></project>",
            "src/main/java/example/App.java", "package example; public class App {}",
            "src/test/java/example/AppTest.java", "package example; class AppTest {}"
    );

    @Override
    public RepositoryHead readHead(Project project) {
        maybeFail();
        return new RepositoryHead(project.getDefaultBranch(), commitSha);
    }

    @Override
    public List<RepositoryFileFact> readTree(Project project, String commitSha) {
        maybeFail();
        return List.of(
                fact("README.md"), fact("pom.xml"), fact("src/main/java/example/App.java"),
                fact("src/test/java/example/AppTest.java"),
                new RepositoryFileFact("assets/logo.png", 1024, "binary-hash"),
                new RepositoryFileFact(".env", 40, "secret-hash"),
                new RepositoryFileFact("docs/large.md", 300000, "large-hash")
        );
    }

    @Override
    public List<RepositoryFileContent> readFiles(Project project, String commitSha, List<String> paths) {
        maybeFail();
        lastRequestedPaths = List.copyOf(paths);
        return paths.stream().filter(content::containsKey)
                .map(path -> new RepositoryFileContent(path, "hash-" + path, content.get(path))).toList();
    }

    public void useCommit(String commitSha) { this.commitSha = commitSha; }
    public void failWith(String message, boolean retryable) { this.failure = new ProviderSyncException(message, retryable); }
    public List<String> getLastRequestedPaths() { return lastRequestedPaths; }
    public void reset() {
        commitSha = "1111111111111111111111111111111111111111";
        failure = null;
        lastRequestedPaths = List.of();
    }

    private RepositoryFileFact fact(String path) {
        String value = content.get(path);
        return new RepositoryFileFact(path, value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                "hash-" + path);
    }

    private void maybeFail() {
        if (failure != null) throw failure;
    }
}
