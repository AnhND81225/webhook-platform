package com.webhookplatform.webhook.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WebhookSigningCryptoTest {
    private static final String MASTER = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    void producesTheKnownHmacSha256Vector() {
        assertThat(WebhookSigner.sign("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8), "1700000000",
                "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("v1=cc1176242319c7fb8b86dc6a691e56b93977cbda0e0cd85ec83ea3766bd4080f");
    }

    @Test
    void encryptsWithFreshNonceAndRejectsTampering() {
        WebhookSecretEncryptionService encryption = new WebhookSecretEncryptionService(new WebhookSigningProperties(MASTER, 1));
        UUID endpoint = UUID.randomUUID();
        byte[] plaintext = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);
        EncryptedSecret one = encryption.encrypt(endpoint, plaintext);
        EncryptedSecret two = encryption.encrypt(endpoint, plaintext);

        assertThat(one.nonce()).hasSize(12);
        assertThat(one.ciphertext()).isNotEqualTo(plaintext);
        assertThat(one.ciphertext()).isNotEqualTo(two.ciphertext());
        assertThat(encryption.decrypt(endpoint, one.ciphertext(), one.nonce(), 1)).isEqualTo(plaintext);
        byte[] tampered = one.ciphertext().clone(); tampered[0] ^= 1;
        assertThatThrownBy(() -> encryption.decrypt(endpoint, tampered, one.nonce(), 1)).isInstanceOf(SigningException.class);
        assertThatThrownBy(() -> encryption.decrypt(UUID.randomUUID(), one.ciphertext(), one.nonce(), 1)).isInstanceOf(SigningException.class);
    }
}
