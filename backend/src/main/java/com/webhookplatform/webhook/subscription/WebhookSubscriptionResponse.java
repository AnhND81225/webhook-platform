package com.webhookplatform.webhook.subscription;

import java.time.Instant;
import java.util.UUID;

public record WebhookSubscriptionResponse(UUID id, String eventType, Instant createdAt) {

    static WebhookSubscriptionResponse from(WebhookSubscription subscription) {
        return new WebhookSubscriptionResponse(
                subscription.getId(), subscription.getEventType(), subscription.getCreatedAt());
    }
}
