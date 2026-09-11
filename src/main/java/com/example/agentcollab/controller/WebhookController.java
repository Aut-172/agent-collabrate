package com.example.agentcollab.controller;

import com.example.agentcollab.dto.WebhookDtos;
import com.example.agentcollab.service.WebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks/git")
public class WebhookController {
    private final WebhookService webhooks;
    public WebhookController(WebhookService webhooks) { this.webhooks = webhooks; }

    @PostMapping("/{provider}")
    public ResponseEntity<WebhookDtos.ReceiveResponse> receive(
            @PathVariable String provider,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody(required = false) String body) {
        WebhookDtos.ReceiveResponse response = webhooks.receive(provider, deliveryId, eventType, signature, body);
        return ResponseEntity.status(response.signatureValid() ? HttpStatus.ACCEPTED : HttpStatus.UNAUTHORIZED)
                .body(response);
    }
}
