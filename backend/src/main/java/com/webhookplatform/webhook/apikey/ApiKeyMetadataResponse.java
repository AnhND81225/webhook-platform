package com.webhookplatform.webhook.apikey;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyMetadataResponse(
        UUID id,
        String name,
        String keyPrefix,
        ApiKeyStatus status,
        Instant lastUsedAt,
        Instant createdAt,
        Instant revokedAt) {

    static ApiKeyMetadataResponse from(ApiKey apiKey) {
        return new ApiKeyMetadataResponse(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getKeyPrefix(),
                apiKey.getStatus(),
                apiKey.getLastUsedAt(),
                apiKey.getCreatedAt(),
                apiKey.getRevokedAt());
    }
}
