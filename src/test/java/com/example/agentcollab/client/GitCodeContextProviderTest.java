package com.example.agentcollab.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitCodeContextProviderTest {
    @Test
    void parsesSupportedGithubRepositoryUrlsWithoutCredentials() {
        var repository = GitCodeContextProvider.parseRepository("https://github.com/example/service.git");

        assertThat(repository.owner()).isEqualTo("example");
        assertThat(repository.repository()).isEqualTo("service");
    }

    @Test
    void rejectsNonGithubAndMalformedRepositoryUrls() {
        assertThatThrownBy(() -> GitCodeContextProvider.parseRepository("https://gitlab.com/example/service"))
                .isInstanceOf(ProviderSyncException.class)
                .hasMessage("Unsupported Git repository URL");
        assertThatThrownBy(() -> GitCodeContextProvider.parseRepository("git@github.com:example/service.git"))
                .isInstanceOf(ProviderSyncException.class)
                .hasMessage("Unsupported Git repository URL");
    }
}
