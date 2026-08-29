package com.webhookplatform.webhook.subscription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateWebhookSubscriptionRequest(
        @NotBlank @Size(max = 128)
        @Pattern(regexp = "^[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)+$") String eventType) {

    public CreateWebhookSubscriptionRequest {
        if (eventType != null) {
            eventType = eventType.trim();
        }
    }
}
