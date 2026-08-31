package com.webhookplatform.webhook.signature;

import jakarta.validation.constraints.NotBlank;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("webhook-platform.signing")
public record WebhookSigningProperties(@NotBlank String masterKey, int keyVersion) {
    public WebhookSigningProperties {
        if (keyVersion < 1) throw new IllegalArgumentException("Signing key version must be positive.");
        if (masterKey != null && !masterKey.isBlank()) {
            try {
                if (Base64.getDecoder().decode(masterKey).length != 32) {
                    throw new IllegalArgumentException("Signing master key must decode to 32 bytes.");
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Signing master key must be Base64 encoded and 32 bytes.", exception);
            }
        }
    }
}
