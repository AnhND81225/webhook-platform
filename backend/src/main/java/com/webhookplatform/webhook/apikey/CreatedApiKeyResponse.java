package com.webhookplatform.webhook.apikey;

import java.time.Instant;
import java.util.UUID;

public record CreatedApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        String apiKey,
        ApiKeyStatus status,
        Instant lastUsedAt,
        Instant createdAt,
        Instant revokedAt) {

    static CreatedApiKeyResponse from(ApiKey apiKey, String rawKey) {
        return new CreatedApiKeyResponse(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getKeyPrefix(),
                rawKey,
                apiKey.getStatus(),
                apiKey.getLastUsedAt(),
                apiKey.getCreatedAt(),
                apiKey.getRevokedAt());
    }
}
