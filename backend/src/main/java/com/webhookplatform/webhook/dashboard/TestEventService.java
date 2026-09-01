package com.webhookplatform.webhook.dashboard;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.webhookplatform.webhook.application.ApplicationService;
import com.webhookplatform.webhook.event.CreateWebhookEventRequest;
import com.webhookplatform.webhook.event.WebhookEventService;

@Service
class TestEventService {
    private final ApplicationService applications;
    private final WebhookEventService events;

    TestEventService(ApplicationService applications, WebhookEventService events) {
        this.applications = applications;
        this.events = events;
    }

    WebhookEventService.IngestionResult send(UUID applicationId, UUID userId, CreateWebhookEventRequest request) {
        applications.requireOwnedApplication(applicationId, userId);
        return events.ingestForApplication(applicationId, request);
    }
}
