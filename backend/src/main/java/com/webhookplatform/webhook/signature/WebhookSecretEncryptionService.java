package com.webhookplatform.webhook.signature;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

@Component
class WebhookSecretEncryptionService {
    private static final int NONCE_BYTES = 12;
    private final WebhookSigningProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    WebhookSecretEncryptionService(WebhookSigningProperties properties) { this.properties = properties; }

    EncryptedSecret encrypt(UUID endpointId, byte[] plaintext) {
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey(), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(endpointId, properties.keyVersion()));
            return new EncryptedSecret(cipher.doFinal(plaintext), nonce, properties.keyVersion());
        } catch (GeneralSecurityException exception) {
            throw new SigningException("Could not encrypt webhook signing secret.", exception);
        }
    }

    byte[] decrypt(UUID endpointId, byte[] ciphertext, byte[] nonce, int keyVersion) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, masterKey(), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(endpointId, keyVersion));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException | ProviderException exception) {
            throw new SigningException("Could not decrypt webhook signing secret.", exception);
        }
    }

    private SecretKeySpec masterKey() {
        try {
            byte[] bytes = Base64.getDecoder().decode(properties.masterKey());
            if (bytes.length != 32) throw new SigningException("Webhook signing master key must decode to 32 bytes.");
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException exception) {
            throw new SigningException("Webhook signing master key must be Base64 encoded.", exception);
        }
    }

    private byte[] aad(UUID endpointId, int keyVersion) {
        return (endpointId + ":" + keyVersion).getBytes(StandardCharsets.UTF_8);
    }
}
