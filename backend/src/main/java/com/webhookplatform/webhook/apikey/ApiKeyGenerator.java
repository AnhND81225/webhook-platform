package com.webhookplatform.webhook.apikey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.webhookplatform.webhook.application.ApplicationEnvironment;

@Component
public class ApiKeyGenerator {

    private static final int SECRET_BYTES = 32;
    private static final int DISPLAY_SECRET_CHARACTERS = 4;

    private final SecureRandom secureRandom;

    public ApiKeyGenerator() {
        this(new SecureRandom());
    }

    ApiKeyGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public GeneratedApiKey generate(ApplicationEnvironment environment) {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        String marker = environment == ApplicationEnvironment.PRODUCTION ? "whk_live_" : "whk_test_";
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String rawKey = marker + secret;
        return new GeneratedApiKey(
                rawKey,
                marker + secret.substring(0, DISPLAY_SECRET_CHARACTERS),
                sha256(rawKey));
    }

    static String sha256(String rawKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record GeneratedApiKey(String rawKey, String prefix, String hash) {
    }
}
