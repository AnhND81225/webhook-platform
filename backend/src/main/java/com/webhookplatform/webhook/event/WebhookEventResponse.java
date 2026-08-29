package com.webhookplatform.webhook.event;

import java.time.Instant;
import java.util.UUID;

public record WebhookEventResponse(UUID id, String sourceEventId, String eventType, Instant createdAt) {

    static WebhookEventResponse from(WebhookEvent event) {
        return new WebhookEventResponse(
                event.getId(), event.getSourceEventId(), event.getEventType(), event.getCreatedAt());
    }
}
