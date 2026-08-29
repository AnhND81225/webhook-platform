package com.webhookplatform.webhook.subscription;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webhookplatform.webhook.endpoint.WebhookEndpoint;
import com.webhookplatform.webhook.endpoint.WebhookEndpointService;

@Service
public class WebhookSubscriptionService {

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookEndpointService endpointService;
    private final Clock clock;

    public WebhookSubscriptionService(
            WebhookSubscriptionRepository subscriptionRepository,
            WebhookEndpointService endpointService,
            Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.endpointService = endpointService;
        this.clock = clock;
    }

    @Transactional
    public WebhookSubscriptionResponse create(
            UUID applicationId, UUID endpointId, UUID ownerUserId, CreateWebhookSubscriptionRequest request) {
        WebhookEndpoint endpoint = endpointService.requireOwnedEndpoint(applicationId, endpointId, ownerUserId);
        try {
            return WebhookSubscriptionResponse.from(subscriptionRepository.saveAndFlush(
                    WebhookSubscription.create(endpoint, request.eventType(), clock.instant())));
        } catch (DataIntegrityViolationException exception) {
            String detail = exception.getMostSpecificCause().getMessage();
            if (detail != null && detail.contains("uq_webhook_subscriptions_endpoint_event_type")) {
                throw new WebhookSubscriptionConflictException();
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<WebhookSubscriptionResponse> list(UUID applicationId, UUID endpointId, UUID ownerUserId) {
        endpointService.requireOwnedEndpoint(applicationId, endpointId, ownerUserId);
        return subscriptionRepository.findAllByEndpointIdOrderByCreatedAtDescIdDesc(endpointId)
                .stream()
                .map(WebhookSubscriptionResponse::from)
                .toList();
    }

    @Transactional
    public void delete(UUID applicationId, UUID endpointId, UUID subscriptionId, UUID ownerUserId) {
        endpointService.requireOwnedEndpoint(applicationId, endpointId, ownerUserId);
        WebhookSubscription subscription = subscriptionRepository
                .findByIdAndEndpointIdAndEndpointApplicationOwnerUserId(subscriptionId, endpointId, ownerUserId)
                .orElseThrow(WebhookSubscriptionNotFoundException::new);
        subscriptionRepository.delete(subscription);
    }
}
