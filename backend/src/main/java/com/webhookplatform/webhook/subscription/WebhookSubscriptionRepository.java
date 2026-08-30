package com.webhookplatform.webhook.subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {

    List<WebhookSubscription> findAllByEndpointIdOrderByCreatedAtDescIdDesc(UUID endpointId);

    Optional<WebhookSubscription> findByIdAndEndpointIdAndEndpointApplicationOwnerUserId(
            UUID id, UUID endpointId, UUID ownerUserId);

    @Query("select new com.webhookplatform.webhook.subscription.WebhookSubscriptionRepository$ActiveEndpointTarget(e, e.url) "
            + "from WebhookSubscription s join s.endpoint e where e.application.id = :applicationId "
            + "and s.eventType = :eventType and e.status = com.webhookplatform.webhook.endpoint.EndpointStatus.ACTIVE")
    List<ActiveEndpointTarget> findActiveEndpointTargets(@Param("applicationId") UUID applicationId, @Param("eventType") String eventType);

    record ActiveEndpointTarget(com.webhookplatform.webhook.endpoint.WebhookEndpoint endpoint, String url) {}
}
