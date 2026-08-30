package com.webhookplatform.webhook.delivery;

import java.time.Instant;
import java.util.UUID;
import com.webhookplatform.webhook.endpoint.WebhookEndpoint;
import com.webhookplatform.webhook.event.WebhookEvent;
import jakarta.persistence.*;

@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "event_id", nullable = false, updatable = false) private WebhookEvent event;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "endpoint_id", nullable = false, updatable = false) private WebhookEndpoint endpoint;
    @Column(name = "target_url", nullable = false, length = 2048, updatable = false) private String targetUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private WebhookDeliveryStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected WebhookDelivery() {}
    static WebhookDelivery create(WebhookEvent event, WebhookEndpoint endpoint, String targetUrl, Instant now) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.event = event; delivery.endpoint = endpoint; delivery.targetUrl = targetUrl;
        delivery.status = WebhookDeliveryStatus.PENDING; delivery.createdAt = now; delivery.updatedAt = now;
        return delivery;
    }
    public UUID getId() { return id; }
    public WebhookEvent getEvent() { return event; }
    public WebhookEndpoint getEndpoint() { return endpoint; }
    public String getTargetUrl() { return targetUrl; }
    public WebhookDeliveryStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
