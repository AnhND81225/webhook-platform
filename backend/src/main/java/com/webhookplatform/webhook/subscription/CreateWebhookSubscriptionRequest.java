package com.webhookplatform.webhook.subscription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.webhookplatform.webhook.common.validation.EventTypeConstraints;

public record CreateWebhookSubscriptionRequest(
        @NotBlank @Size(max = 128)
        @Pattern(regexp = EventTypeConstraints.PATTERN) String eventType) {

    public CreateWebhookSubscriptionRequest {
        if (eventType != null) {
            eventType = eventType.trim();
        }
    }
}
