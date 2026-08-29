package com.webhookplatform.webhook.subscription;

import java.time.Instant;
import java.util.UUID;

import com.webhookplatform.webhook.endpoint.WebhookEndpoint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "webhook_subscriptions")
public class WebhookSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false, updatable = false)
    private WebhookEndpoint endpoint;

    @Column(name = "event_type", nullable = false, length = 128, updatable = false)
    private String eventType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WebhookSubscription() {
    }

    static WebhookSubscription create(WebhookEndpoint endpoint, String eventType, Instant now) {
        WebhookSubscription subscription = new WebhookSubscription();
        subscription.endpoint = endpoint;
        subscription.eventType = eventType;
        subscription.createdAt = now;
        return subscription;
    }

    public UUID getId() { return id; }
    public WebhookEndpoint getEndpoint() { return endpoint; }
    public String getEventType() { return eventType; }
    public Instant getCreatedAt() { return createdAt; }
}
