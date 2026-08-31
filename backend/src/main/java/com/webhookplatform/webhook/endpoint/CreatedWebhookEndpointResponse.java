package com.webhookplatform.webhook.endpoint;

import java.time.Instant;
import java.util.UUID;

public record CreatedWebhookEndpointResponse(UUID id, String name, String url, EndpointStatus status,
        Instant createdAt, Instant updatedAt, String signingSecret) { }
