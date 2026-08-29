package com.webhookplatform.webhook.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.webhookplatform.webhook.common.validation.EventTypeConstraints;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateWebhookEventRequest(
        @NotBlank @Size(max = 255) String sourceEventId,
        @NotBlank @Size(max = 128) @Pattern(regexp = EventTypeConstraints.PATTERN) String eventType,
        @NotNull JsonNode payload) {

    public CreateWebhookEventRequest {
        if (sourceEventId != null) {
            sourceEventId = sourceEventId.trim();
        }
        if (eventType != null) {
            eventType = eventType.trim();
        }
    }
}
