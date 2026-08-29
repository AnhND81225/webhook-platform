package com.webhookplatform.webhook.application;

import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        String name,
        String slug,
        ApplicationStatus status,
        ApplicationEnvironment environment,
        Instant createdAt,
        Instant updatedAt) {

    static ApplicationResponse from(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getName(),
                application.getSlug(),
                application.getStatus(),
                application.getEnvironment(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }
}
