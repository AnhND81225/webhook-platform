package com.webhookplatform.webhook.apikey;

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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private Application application;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 20, updatable = false)
    private String keyPrefix;

    @Column(
            name = "key_hash",
            nullable = false,
            length = 64,
            unique = true,
            updatable = false,
            columnDefinition = "CHAR(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String keyHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApiKeyStatus status;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiKey() {
    }

    static ApiKey create(
            Application application,
            String name,
            String keyPrefix,
            String keyHash,
            Instant now) {
        ApiKey apiKey = new ApiKey();
        apiKey.application = application;
        apiKey.name = name;
        apiKey.keyPrefix = keyPrefix;
        apiKey.keyHash = keyHash;
        apiKey.status = ApiKeyStatus.ACTIVE;
        apiKey.createdAt = now;
        return apiKey;
    }

    void revoke(Instant now) {
        if (status == ApiKeyStatus.ACTIVE) {
            status = ApiKeyStatus.REVOKED;
            revokedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public Application getApplication() {
        return application;
    }

    public String getName() {
        return name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    String getKeyHash() {
        return keyHash;
    }

    public ApiKeyStatus getStatus() {
        return status;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
