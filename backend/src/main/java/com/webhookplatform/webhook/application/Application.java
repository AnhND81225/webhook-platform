package com.webhookplatform.webhook.application;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "applications")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private UUID ownerUserId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 63, updatable = false)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApplicationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private ApplicationEnvironment environment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Application() {
    }

    static Application create(
            UUID ownerUserId,
            String name,
            String slug,
            ApplicationEnvironment environment,
            Instant now) {
        Application application = new Application();
        application.ownerUserId = ownerUserId;
        application.name = name;
        application.slug = slug;
        application.status = ApplicationStatus.ACTIVE;
        application.environment = environment;
        application.createdAt = now;
        application.updatedAt = now;
        return application;
    }

    void update(String name, ApplicationStatus status, Instant now) {
        if (name != null) {
            this.name = name;
        }
        if (status != null) {
            this.status = status;
        }
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public ApplicationEnvironment getEnvironment() {
        return environment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
