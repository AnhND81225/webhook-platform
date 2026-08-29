package com.webhookplatform.webhook.event;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.webhookplatform.webhook.security.ProducerPrincipal;

@Service
public class WebhookEventService {

    private final WebhookEventPersistenceService persistenceService;

    public WebhookEventService(WebhookEventPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public IngestionResult ingest(ProducerPrincipal producer, CreateWebhookEventRequest request) {
        validatePayload(request);
        try {
            return toResult(persistenceService.persist(producer, request));
        } catch (DataIntegrityViolationException exception) {
            Optional<WebhookEventPersistenceService.PersistedEvent> racedEvent =
                    persistenceService.resolveAfterUniqueRace(producer, request);
            if (racedEvent.isPresent()) {
                return toResult(racedEvent.get());
            }
            throw exception;
        }
    }

    private void validatePayload(CreateWebhookEventRequest request) {
        if (!request.payload().isObject()) {
            throw new InvalidWebhookEventException("payload must be a JSON object.");
        }
    }

    private IngestionResult toResult(WebhookEventPersistenceService.PersistedEvent persistedEvent) {
        return new IngestionResult(WebhookEventResponse.from(persistedEvent.event()), persistedEvent.created());
    }

    public record IngestionResult(WebhookEventResponse event, boolean created) {
    }
}
