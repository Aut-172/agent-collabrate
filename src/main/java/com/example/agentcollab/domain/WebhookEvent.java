package com.example.agentcollab.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "webhook_events", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "delivery_id"}))
public class WebhookEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 30, updatable = false) private String provider;
    @Column(name = "delivery_id", nullable = false, length = 200, updatable = false) private String deliveryId;
    @Column(name = "event_type", nullable = false, length = 100, updatable = false) private String eventType;
    @Column(name = "signature_valid", nullable = false, updatable = false) private boolean signatureValid;
    @Column(name = "payload_hash", nullable = false, length = 128, updatable = false) private String payloadHash;
    @Enumerated(EnumType.STRING) @Column(name = "processing_status", nullable = false, length = 30) private WebhookEventStatus processingStatus;
    @Column(name = "received_at", nullable = false, updatable = false) private Instant receivedAt;
    @Column(name = "processed_at") private Instant processedAt;

    protected WebhookEvent() {}

    public WebhookEvent(String provider, String deliveryId, String eventType, boolean signatureValid, String payloadHash) {
        this.provider = provider; this.deliveryId = deliveryId; this.eventType = eventType;
        this.signatureValid = signatureValid; this.payloadHash = payloadHash;
        this.processingStatus = WebhookEventStatus.RECEIVED; this.receivedAt = Instant.now();
    }

    public void markProcessed() { processingStatus = WebhookEventStatus.PROCESSED; processedAt = Instant.now(); }
    public void markIgnored() { processingStatus = WebhookEventStatus.IGNORED; processedAt = Instant.now(); }
    public void markFailed() { processingStatus = WebhookEventStatus.FAILED; processedAt = Instant.now(); }
    public Long getId() { return id; }
    public String getProvider() { return provider; }
    public String getDeliveryId() { return deliveryId; }
    public String getEventType() { return eventType; }
    public boolean isSignatureValid() { return signatureValid; }
    public String getPayloadHash() { return payloadHash; }
    public WebhookEventStatus getProcessingStatus() { return processingStatus; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getProcessedAt() { return processedAt; }
}
