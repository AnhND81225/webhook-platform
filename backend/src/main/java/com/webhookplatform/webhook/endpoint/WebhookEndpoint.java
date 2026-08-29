package com.webhookplatform.webhook.endpoint;

import java.time.Instant;
import java.util.UUID;

import com.webhookplatform.webhook.application.Application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private Application application;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EndpointStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebhookEndpoint() {
    }

    static WebhookEndpoint create(Application application, String name, String url, Instant now) {
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.application = application;
        endpoint.name = name;
        endpoint.url = url;
        endpoint.status = EndpointStatus.ACTIVE;
        endpoint.createdAt = now;
        endpoint.updatedAt = now;
        return endpoint;
    }

    void update(String name, String url, EndpointStatus status, Instant now) {
        if (name != null) {
            this.name = name;
        }
        if (url != null) {
            this.url = url;
        }
        if (status != null) {
            this.status = status;
        }
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public Application getApplication() { return application; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public EndpointStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
