package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.dto.WebhookDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class WebhookService {
    private final WebhookEventRepository events;
    private final ProjectRepository projects;
    private final TaskDeliveryRepository deliveries;
    private final CiRunRepository ciRuns;
    private final OutboxJobRepository jobs;
    private final ObjectMapper json;
    private final String webhookSecret;

    public WebhookService(WebhookEventRepository events, ProjectRepository projects,
                          TaskDeliveryRepository deliveries, CiRunRepository ciRuns,
                          OutboxJobRepository jobs, ObjectMapper json,
                          @Value("${app.ci.webhook-secret:}") String webhookSecret) {
        this.events = events; this.projects = projects; this.deliveries = deliveries;
        this.ciRuns = ciRuns; this.jobs = jobs; this.json = json; this.webhookSecret = webhookSecret;
    }

    @Transactional
    public WebhookDtos.ReceiveResponse receive(String provider, String deliveryId, String eventType,
                                                String signature, String body) {
        String normalizedProvider = provider == null || provider.isBlank() ? "unknown" : provider.toLowerCase(Locale.ROOT);
        String payload = body == null ? "" : body;
        String payloadHash = sha256(payload);
        String id = deliveryId == null || deliveryId.isBlank() ? "missing-" + payloadHash.substring(0, 24) : deliveryId;
        String type = eventType == null || eventType.isBlank() ? "unknown" : eventType;
        boolean signatureValid = verifySignature(payload, signature);
        Optional<WebhookEvent> existing = events.findByProviderAndDeliveryIdForUpdate(normalizedProvider, id);
        if (existing.isPresent()) {
            if (!signatureValid) {
                WebhookEvent prior = existing.get();
                return new WebhookDtos.ReceiveResponse(prior.getId(), prior.getProvider(), prior.getDeliveryId(),
                        prior.getProcessingStatus(), false, true, false);
            }
            return WebhookDtos.ReceiveResponse.from(existing.get(), true, false);
        }

        WebhookEvent event = events.save(new WebhookEvent(normalizedProvider, id, type, signatureValid, "sha256:" + payloadHash));
        if (!signatureValid) {
            event.markFailed();
            return WebhookDtos.ReceiveResponse.from(event, false, false);
        }
        if (!"github".equals(normalizedProvider)) {
            event.markIgnored();
            return WebhookDtos.ReceiveResponse.from(event, false, false);
        }
        try {
            JsonNode root = json.readTree(payload);
            String repository = root.path("repository").path("full_name").asText("");
            String headSha = headSha(root, type);
            if (repository.isBlank() || headSha.isBlank()) {
                event.markIgnored();
                return WebhookDtos.ReceiveResponse.from(event, false, false);
            }
            Project project = findProject(repository).orElse(null);
            if (project == null) {
                event.markIgnored();
                return WebhookDtos.ReceiveResponse.from(event, false, false);
            }
            TaskDelivery delivery = deliveries.findLatestByProjectIdAndCommitSha(project.getId(), headSha).orElse(null);
            if (delivery == null || !isLatestForTask(delivery) || delivery.getStatus() != TaskDeliveryStatus.CI_RUNNING) {
                event.markIgnored();
                return WebhookDtos.ReceiveResponse.from(event, false, false);
            }
            CiRun run = ciRuns.findByDeliveryIdAndCommitSha(delivery.getId(), delivery.getCommitSha()).orElse(null);
            if (run == null || run.isTerminal()) {
                event.markIgnored();
                return WebhookDtos.ReceiveResponse.from(event, false, false);
            }
            Optional<OutboxJob> existingJob = jobs.findByJobTypeAndReferenceId(OutboxJobType.CI_SYNC, run.getId());
            boolean enqueued = existingJob.isEmpty();
            if (enqueued) jobs.save(new OutboxJob(OutboxJobType.CI_SYNC, run.getId()));
            event.markProcessed();
            return WebhookDtos.ReceiveResponse.from(event, false, enqueued);
        } catch (Exception ex) {
            event.markIgnored();
            return WebhookDtos.ReceiveResponse.from(event, false, false);
        }
    }

    private boolean isLatestForTask(TaskDelivery delivery) {
        return deliveries.findTopByTaskIdOrderBySubmittedAtDesc(delivery.getTaskId())
                .map(latest -> latest.getId().equals(delivery.getId())).orElse(false);
    }

    private Optional<Project> findProject(String fullName) {
        String normalized = fullName.toLowerCase(Locale.ROOT);
        return projects.findByGitProviderIgnoreCase("github").stream()
                .filter(project -> normalizeRepository(project.getRepositoryUrl()).equals(normalized))
                .findFirst();
    }

    private String normalizeRepository(String value) {
        try {
            URI uri = URI.create(value);
            String path = uri.getPath();
            while (path.startsWith("/")) path = path.substring(1);
            if (path.endsWith(".git")) path = path.substring(0, path.length() - 4);
            return path.toLowerCase(Locale.ROOT);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private String headSha(JsonNode root, String eventType) {
        return switch (eventType) {
            case "check_run" -> root.path("check_run").path("head_sha").asText("");
            case "workflow_run" -> root.path("workflow_run").path("head_sha").asText("");
            case "pull_request" -> root.path("pull_request").path("head").path("sha").asText("");
            case "push" -> root.path("after").asText("");
            default -> "";
        };
    }

    private boolean verifySignature(String body, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank() || signature == null || !signature.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String actualHex = signature.substring("sha256=".length());
            byte[] actual = HexFormat.of().parseHex(actualHex);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception ex) {
            return false;
        }
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
}
