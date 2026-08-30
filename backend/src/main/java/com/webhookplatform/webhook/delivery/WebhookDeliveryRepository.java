package com.webhookplatform.webhook.delivery;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {}
