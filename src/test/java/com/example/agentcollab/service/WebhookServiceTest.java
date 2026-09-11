package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {
    private static final String SECRET = "webhook-test-secret";
    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
    @Mock WebhookEventRepository events;
    @Mock ProjectRepository projects;
    @Mock TaskDeliveryRepository deliveries;
    @Mock CiRunRepository ciRuns;
    @Mock OutboxJobRepository jobs;

    @Test
    void invalidSignatureIsPersistedButCannotChangeCiState() {
        when(events.findByProviderAndDeliveryIdForUpdate("github", "delivery-1")).thenReturn(Optional.empty());
        when(events.save(any(WebhookEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        WebhookService service = service();

        var response = service.receive("github", "delivery-1", "check_run", "sha256=bad",
                "{\"repository\":{\"full_name\":\"acme/app\"}}");

        assertThat(response.signatureValid()).isFalse();
        assertThat(response.processingStatus()).isEqualTo(WebhookEventStatus.FAILED);
        verifyNoInteractions(projects, deliveries, ciRuns, jobs);
    }

    @Test
    void duplicateDeliveryIsIdempotentAndDoesNotEnqueueAgain() {
        WebhookEvent existing = new WebhookEvent("github", "delivery-2", "check_run", true, "sha256:hash");
        ReflectionTestUtils.setField(existing, "id", 7L);
        existing.markProcessed();
        when(events.findByProviderAndDeliveryIdForUpdate("github", "delivery-2")).thenReturn(Optional.of(existing));

        var response = service().receive("github", "delivery-2", "check_run", validSignature("{}"), "{}");

        assertThat(response.duplicate()).isTrue();
        assertThat(response.eventId()).isEqualTo(7L);
        verify(events, never()).save(any());
        verifyNoInteractions(jobs);
    }

    @Test
    void invalidSignatureNeverTurnsAValidDuplicateIntoAnAcceptedRequest() {
        WebhookEvent existing = new WebhookEvent("github", "delivery-invalid-duplicate", "check_run", true, "sha256:hash");
        ReflectionTestUtils.setField(existing, "id", 8L);
        existing.markProcessed();
        when(events.findByProviderAndDeliveryIdForUpdate("github", "delivery-invalid-duplicate"))
                .thenReturn(Optional.of(existing));

        var response = service().receive("github", "delivery-invalid-duplicate", "check_run", "sha256=bad", "{}");

        assertThat(response.signatureValid()).isFalse();
        assertThat(response.duplicate()).isTrue();
        verify(events, never()).save(any());
    }

    @Test
    void validCheckRunEnqueuesOnlyTheCurrentDeliveryCiRun() {
        Project project = new Project("app", "https://github.com/acme/app.git", "github", "main", 1L);
        ReflectionTestUtils.setField(project, "id", 3L);
        TaskDelivery delivery = delivery(11L, SHA, TaskDeliveryStatus.CI_RUNNING);
        CiRun run = new CiRun(3L, 5L, 9L, 11L, SHA);
        when(events.findByProviderAndDeliveryIdForUpdate("github", "delivery-3")).thenReturn(Optional.empty());
        when(events.save(any(WebhookEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projects.findByGitProviderIgnoreCase("github")).thenReturn(java.util.List.of(project));
        when(deliveries.findLatestByProjectIdAndCommitSha(3L, SHA)).thenReturn(Optional.of(delivery));
        when(deliveries.findTopByTaskIdOrderBySubmittedAtDesc(9L)).thenReturn(Optional.of(delivery));
        when(ciRuns.findByDeliveryIdAndCommitSha(11L, SHA)).thenReturn(Optional.of(run));
        when(jobs.findByJobTypeAndReferenceId(OutboxJobType.CI_SYNC, run.getId())).thenReturn(Optional.empty());
        when(jobs.save(any(OutboxJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String body = "{\"repository\":{\"full_name\":\"acme/app\"},\"check_run\":{\"head_sha\":\"" + SHA + "\"}}";

        var response = service().receive("github", "delivery-3", "check_run", validSignature(body), body);

        assertThat(response.signatureValid()).isTrue();
        assertThat(response.processingStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(response.enqueued()).isTrue();
        verify(jobs).save(any(OutboxJob.class));
    }

    @Test
    void unknownRepositoryAndOldShaAreIgnored() {
        when(events.findByProviderAndDeliveryIdForUpdate("github", "delivery-4")).thenReturn(Optional.empty());
        when(events.save(any(WebhookEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projects.findByGitProviderIgnoreCase("github")).thenReturn(java.util.List.of());
        String body = "{\"repository\":{\"full_name\":\"unknown/app\"},\"check_run\":{\"head_sha\":\"" + SHA + "\"}}";

        var response = service().receive("github", "delivery-4", "check_run", validSignature(body), body);

        assertThat(response.processingStatus()).isEqualTo(WebhookEventStatus.IGNORED);
        verifyNoInteractions(deliveries, ciRuns, jobs);
    }

    private WebhookService service() {
        return new WebhookService(events, projects, deliveries, ciRuns, jobs, new ObjectMapper(), SECRET);
    }

    private String validSignature(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private TaskDelivery delivery(long id, String sha, TaskDeliveryStatus status) {
        TaskDelivery delivery = new TaskDelivery(9L, 2L, 3L, 1, null, null, sha,
                new ObjectMapper().createObjectNode(), "feature/task", sha, null);
        ReflectionTestUtils.setField(delivery, "id", id);
        ReflectionTestUtils.setField(delivery, "status", status);
        return delivery;
    }
}
