package com.webhookplatform.webhook.signature;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WebhookSigningSecretRepository extends JpaRepository<WebhookSigningSecret, UUID> {
    Optional<WebhookSigningSecret> findByEndpointId(UUID endpointId);
    boolean existsByEndpointId(UUID endpointId);
}
