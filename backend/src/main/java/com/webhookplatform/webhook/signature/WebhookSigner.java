package com.webhookplatform.webhook.signature;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class WebhookSigner {
    private final WebhookSigningSecretService secrets;
    private final Clock clock;
    WebhookSigner(WebhookSigningSecretService secrets, Clock clock) { this.secrets = secrets; this.clock = clock; }

    public SignedWebhookRequest sign(java.util.UUID endpointId, byte[] body) {
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        byte[] key = secrets.loadKey(endpointId);
        try {
            return new SignedWebhookRequest(timestamp, sign(key, timestamp, body));
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    static String sign(byte[] key, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            return "v1=" + toLowerHex(mac.doFinal(body));
        } catch (GeneralSecurityException exception) {
            throw new SigningException("Could not sign webhook request.", exception);
        }
    }

    private static String toLowerHex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) value.append(String.format("%02x", current));
        return value.toString();
    }
}
