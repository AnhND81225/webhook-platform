package com.webhookplatform.webhook.event;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhookplatform.webhook.application.Application;
import com.webhookplatform.webhook.application.ApplicationRepository;
import com.webhookplatform.webhook.delivery.WebhookDeliveryService;

@Service
class WebhookEventPersistenceService {

    private final WebhookEventRepository eventRepository;
    private final ApplicationRepository applicationRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final WebhookDeliveryService deliveryService;

    WebhookEventPersistenceService(
            WebhookEventRepository eventRepository,
            ApplicationRepository applicationRepository,
            ObjectMapper objectMapper,
            Clock clock, WebhookDeliveryService deliveryService) {
        this.eventRepository = eventRepository;
        this.applicationRepository = applicationRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.deliveryService = deliveryService;
    }

    @Transactional
    public PersistedEvent persist(UUID applicationId, CreateWebhookEventRequest request) {
        Optional<WebhookEvent> existing = eventRepository.findByApplicationIdAndSourceEventId(
                applicationId, request.sourceEventId());
        if (existing.isPresent()) {
            return existingResult(applicationId, request, existing.get());
        }

        Application application = applicationRepository.getReferenceById(applicationId);
        WebhookEvent event = WebhookEvent.create(
                application,
                request.sourceEventId(),
                request.eventType(),
                request.payload(),
                clock.instant());
        WebhookEvent persisted = eventRepository.saveAndFlush(event);
        deliveryService.createFor(persisted);
        return new PersistedEvent(persisted, true);
    }

    @Transactional(readOnly = true)
    public Optional<PersistedEvent> resolveAfterUniqueRace(UUID applicationId, CreateWebhookEventRequest request) {
        return eventRepository.findByApplicationIdAndSourceEventId(applicationId, request.sourceEventId())
                .map(event -> existingResult(applicationId, request, event));
    }

    private PersistedEvent existingResult(
            UUID applicationId, CreateWebhookEventRequest request, WebhookEvent existing) {
        if (!eventRepository.existsExactEvent(
                applicationId, request.sourceEventId(), request.eventType(), serializePayload(request))) {
            throw new SourceEventConflictException();
        }
        return new PersistedEvent(existing, false);
    }

    private String serializePayload(CreateWebhookEventRequest request) {
        try {
            return objectMapper.writeValueAsString(request.payload());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("JSON payload serialization failed", exception);
        }
    }

    record PersistedEvent(WebhookEvent event, boolean created) {
    }
}
