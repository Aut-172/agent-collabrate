package com.example.agentcollab.client;

import com.example.agentcollab.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;

class GitHubProviderClientTest {
    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

    @Test
    void missingCommitIsAValidationFailureAndNotAProviderRetry() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubGitProviderClient provider = new GitHubGitProviderClient(builder, "https://api.github.test", "");
        server.expect(requestTo("https://api.github.test/repos/acme/app/commits/" + SHA))
                .andExpect(method(GET)).andRespond(withResourceNotFound());

        var result = provider.validate(project(), delivery(null));

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("does not exist");
        server.verify();
    }

    @Test
    void pullRequestFromAnotherRepositoryIsRejectedWithoutTrustingItsHead() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubGitProviderClient provider = new GitHubGitProviderClient(builder, "https://api.github.test", "");
        server.expect(requestTo("https://api.github.test/repos/acme/app/commits/" + SHA))
                .andRespond(withSuccess("{\"sha\":\"" + SHA + "\"}", APPLICATION_JSON));
        server.expect(requestTo("https://api.github.test/repos/acme/app/branches/feature/task"))
                .andRespond(withSuccess("{\"name\":\"feature/task\",\"commit\":{\"sha\":\"" + SHA + "\"}}", APPLICATION_JSON));

        var result = provider.validate(project(), delivery("https://github.com/other/app/pull/7"));

        assertThat(result.repositoryMatches()).isTrue();
        assertThat(result.commitExists()).isTrue();
        assertThat(result.pullRequestMatches()).isFalse();
        assertThat(result.isValid()).isFalse();
        server.verify();
    }

    @Test
    void unauthorizedAndServerErrorsAreClassifiedForRetryPolicy() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubGitProviderClient provider = new GitHubGitProviderClient(builder, "https://api.github.test", "");
        server.expect(requestTo("https://api.github.test/repos/acme/app/commits/" + SHA))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(
                        org.springframework.http.HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> provider.validate(project(), delivery(null)))
                .isInstanceOf(ProviderSyncException.class)
                .extracting(ex -> ((ProviderSyncException) ex).isRetryable()).isEqualTo(false);

        server.reset();
        server.expect(requestTo("https://api.github.test/repos/acme/app/commits/" + SHA))
                .andRespond(withServerError());
        assertThatThrownBy(() -> provider.validate(project(), delivery(null)))
                .isInstanceOf(ProviderSyncException.class)
                .extracting(ex -> ((ProviderSyncException) ex).isRetryable()).isEqualTo(true);
    }

    @Test
    void actionsResultsUseCheckRunHeadShaAndRejectOldSha() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubCiProviderClient provider = new GitHubCiProviderClient(builder, "https://api.github.test", "");
        String oldSha = "abcdefabcdefabcdefabcdefabcdefabcdefabcd";
        server.expect(requestTo("https://api.github.test/repos/acme/app/commits/" + SHA + "/check-runs?per_page=100"))
                .andRespond(withSuccess("{\"total_count\":1,\"check_runs\":[{\"id\":4,\"head_sha\":\""
                        + oldSha + "\",\"status\":\"completed\",\"conclusion\":\"success\",\"html_url\":\"https://github.com/acme/app/actions/runs/4\"}]}", APPLICATION_JSON));

        CiRun run = new CiRun(1L, 2L, 3L, 4L, SHA);
        var result = provider.sync(project(), run);

        assertThat(result.status()).isEqualTo(CiRunStatus.PASSED);
        assertThat(result.headSha()).isEqualTo(oldSha);
        assertThat(result.configurationPresent()).isTrue();
        server.verify();
    }

    @Test
    void emptyActionsResultsCannotPassBootstrap() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubCiProviderClient provider = new GitHubCiProviderClient(builder, "https://api.github.test", "");
        server.expect(requestTo("https://api.github.test/repos/acme/app/commits/" + SHA + "/check-runs?per_page=100"))
                .andRespond(withSuccess("{\"total_count\":0,\"check_runs\":[]}", APPLICATION_JSON));

        var result = provider.sync(project(), new CiRun(1L, 2L, 3L, 4L, SHA));

        assertThat(result.status()).isEqualTo(CiRunStatus.UNKNOWN);
        assertThat(result.configurationPresent()).isFalse();
        assertThat(result.configurationRecognized()).isFalse();
        server.verify();
    }

    private Project project() {
        Project project = new Project("app", "https://github.com/acme/app", "github", "main", 1L);
        ReflectionTestUtils.setField(project, "id", 1L);
        return project;
    }

    private TaskDelivery delivery(String pullRequestUrl) {
        TaskDelivery delivery = new TaskDelivery(1L, 2L, 3L, 1, null, null, SHA,
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode(), "feature/task", SHA, pullRequestUrl);
        ReflectionTestUtils.setField(delivery, "id", 4L);
        return delivery;
    }
}
