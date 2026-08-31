package com.webhookplatform.webhook.signature;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.webhookplatform.webhook.endpoint.WebhookEndpoint;
import com.webhookplatform.webhook.endpoint.WebhookEndpointRepository;
import com.webhookplatform.webhook.endpoint.WebhookSigningSecretAlreadyProvisionedException;

@Service
public class WebhookSigningSecretService {
    private final WebhookSigningSecretRepository repository;
    private final WebhookSecretEncryptionService encryption;
    private final Clock clock;
    private final WebhookEndpointRepository endpointRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    WebhookSigningSecretService(WebhookSigningSecretRepository repository, WebhookSecretEncryptionService encryption, Clock clock,
            WebhookEndpointRepository endpointRepository) {
        this.repository = repository; this.encryption = encryption; this.clock = clock; this.endpointRepository = endpointRepository;
    }

    @Transactional
    public ProvisionedSigningSecret provision(WebhookEndpoint endpoint) {
        if (repository.existsByEndpointId(endpoint.getId())) throw new WebhookSigningSecretAlreadyProvisionedException();
        byte[] key = new byte[32];
        secureRandom.nextBytes(key);
        String value = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(key);
        try {
            repository.save(WebhookSigningSecret.create(endpoint, encryption.encrypt(endpoint.getId(), key), clock.instant()));
            return new ProvisionedSigningSecret(value);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    @Transactional
    public ProvisionedSigningSecret provision(UUID endpointId) {
        return provision(endpointRepository.getReferenceById(endpointId));
    }

    @Transactional(readOnly = true)
    byte[] loadKey(UUID endpointId) {
        WebhookSigningSecret secret = repository.findByEndpointId(endpointId)
                .orElseThrow(() -> new SigningException("Webhook endpoint has no signing secret."));
        return encryption.decrypt(endpointId, secret.encryptedSecret(), secret.nonce(), secret.keyVersion());
    }
}
