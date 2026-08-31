package com.webhookplatform.webhook.signature;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import com.webhookplatform.webhook.endpoint.WebhookEndpoint;

@Entity
@Table(name = "webhook_signing_secrets")
class WebhookSigningSecret {
    @Id private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "endpoint_id", nullable = false, unique = true, updatable = false)
    private WebhookEndpoint endpoint;
    @Column(name = "encrypted_secret", nullable = false) private byte[] encryptedSecret;
    @Column(nullable = false) private byte[] nonce;
    @Column(name = "key_version", nullable = false) private int keyVersion;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected WebhookSigningSecret() { }
    static WebhookSigningSecret create(WebhookEndpoint endpoint, EncryptedSecret encrypted, Instant now) {
        WebhookSigningSecret secret = new WebhookSigningSecret();
        secret.id = UUID.randomUUID(); secret.endpoint = endpoint; secret.encryptedSecret = encrypted.ciphertext();
        secret.nonce = encrypted.nonce(); secret.keyVersion = encrypted.keyVersion(); secret.createdAt = now; secret.updatedAt = now;
        return secret;
    }
    UUID endpointId() { return endpoint.getId(); }
    byte[] encryptedSecret() { return encryptedSecret; }
    byte[] nonce() { return nonce; }
    int keyVersion() { return keyVersion; }
}
