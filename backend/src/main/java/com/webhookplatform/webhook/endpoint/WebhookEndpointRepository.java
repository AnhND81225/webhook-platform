package com.webhookplatform.webhook.endpoint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    List<WebhookEndpoint> findAllByApplicationIdOrderByCreatedAtDescIdDesc(UUID applicationId);

    Optional<WebhookEndpoint> findByIdAndApplicationIdAndApplicationOwnerUserId(
            UUID id, UUID applicationId, UUID ownerUserId);
}
