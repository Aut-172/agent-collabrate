package com.example.agentcollab.client;

import com.example.agentcollab.domain.Project;
import java.util.List;

/** Returns requested repository facts only; semantic relevance is decided by the platform Agent. */
public interface CodeContextProvider {
    RepositoryHead readHead(Project project);
    List<RepositoryFileFact> readTree(Project project, String commitSha);
    List<RepositoryFileContent> readFiles(Project project, String commitSha, List<String> paths);

    record RepositoryHead(String branchName, String commitSha) {}
    record RepositoryFileFact(String path, long sizeBytes, String contentHash) {}
    record RepositoryFileContent(String path, String contentHash, String content) {}
}
