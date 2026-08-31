package com.webhookplatform.webhook.delivery;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;
import com.webhookplatform.webhook.signature.SignedWebhookRequest;
import com.webhookplatform.webhook.signature.WebhookSigner;

@Component
class OutboundWebhookClient {

    private final DestinationAddressPolicy destinationAddressPolicy;
    private final WebhookWorkerProperties properties;
    private final WebhookSigner signer;

    OutboundWebhookClient(DestinationAddressPolicy destinationAddressPolicy, WebhookWorkerProperties properties, WebhookSigner signer) {
        this.destinationAddressPolicy = destinationAddressPolicy;
        this.properties = properties;
        this.signer = signer;
    }

    int post(ClaimedDelivery delivery, byte[] body) throws IOException {
        SignedWebhookRequest signed = signer.sign(delivery.endpointId(), body);
        URI target = URI.create(delivery.targetUrl());
        boolean allowDevHttpLocalhost = "http".equalsIgnoreCase(target.getScheme())
                && ("localhost".equalsIgnoreCase(target.getHost()) || "127.0.0.1".equals(target.getHost()));
        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(timeout(properties.requestTimeout()))
                .build();
        try (CloseableHttpClient client = HttpClients.custom()
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new ValidatingDnsResolver(destinationAddressPolicy, allowDevHttpLocalhost))
                        .setDefaultConnectionConfig(org.apache.hc.client5.http.config.ConnectionConfig.custom()
                                .setConnectTimeout(timeout(properties.connectTimeout()))
                                .build())
                        .build())
                .setDefaultRequestConfig(requestConfig)
                .build()) {
            HttpPost request = new HttpPost(delivery.targetUrl());
            request.setEntity(new ByteArrayEntity(body, ContentType.APPLICATION_JSON));
            request.setHeader("User-Agent", "webhook-platform/1.0");
            request.setHeader("X-Webhook-Id", delivery.eventId().toString());
            request.setHeader("X-Webhook-Delivery-Id", delivery.deliveryId().toString());
            request.setHeader("X-Webhook-Event", delivery.eventType());
            request.setHeader("X-Webhook-Timestamp", signed.timestamp());
            request.setHeader("X-Webhook-Signature", signed.signature());
            return client.execute(request, response -> response.getCode());
        }
    }

    private Timeout timeout(Duration duration) {
        return Timeout.ofMilliseconds(duration.toMillis());
    }
}
