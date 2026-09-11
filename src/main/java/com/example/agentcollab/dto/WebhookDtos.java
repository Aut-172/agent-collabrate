package com.example.agentcollab.dto;

import com.example.agentcollab.domain.WebhookEvent;
import com.example.agentcollab.domain.WebhookEventStatus;

public final class WebhookDtos {
    private WebhookDtos() {}
    public record ReceiveResponse(Long eventId, String provider, String deliveryId,
                                  WebhookEventStatus processingStatus, boolean signatureValid,
                                  boolean duplicate, boolean enqueued) {
        public static ReceiveResponse from(WebhookEvent event, boolean duplicate, boolean enqueued) {
            return new ReceiveResponse(event.getId(), event.getProvider(), event.getDeliveryId(),
                    event.getProcessingStatus(), event.isSignatureValid(), duplicate, enqueued);
        }
    }
}
