package com.webhookplatform.webhook.event;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.webhookplatform.webhook.security.ProducerPrincipal;

@RestController
@RequestMapping("/api/v1/events")
public class WebhookEventController {

    private final WebhookEventService eventService;

    public WebhookEventController(WebhookEventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<WebhookEventResponse> create(
            @AuthenticationPrincipal ProducerPrincipal producer,
            @Valid @RequestBody CreateWebhookEventRequest request) {
        WebhookEventService.IngestionResult result = eventService.ingest(producer, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.event());
    }
}
