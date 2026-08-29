package com.webhookplatform.webhook.endpoint;

import java.time.Instant;
import java.util.UUID;

public record WebhookEndpointResponse(
        UUID id,
        String name,
        String url,
        EndpointStatus status,
        Instant createdAt,
        Instant updatedAt) {

    static WebhookEndpointResponse from(WebhookEndpoint endpoint) {
        return new WebhookEndpointResponse(
                endpoint.getId(), endpoint.getName(), endpoint.getUrl(), endpoint.getStatus(),
                endpoint.getCreatedAt(), endpoint.getUpdatedAt());
    }
}
