package com.webhookplatform.webhook.subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {

    List<WebhookSubscription> findAllByEndpointIdOrderByCreatedAtDescIdDesc(UUID endpointId);

    Optional<WebhookSubscription> findByIdAndEndpointIdAndEndpointApplicationOwnerUserId(
            UUID id, UUID endpointId, UUID ownerUserId);
}
