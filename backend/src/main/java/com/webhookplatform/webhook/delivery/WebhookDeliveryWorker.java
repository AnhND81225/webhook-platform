package com.webhookplatform.webhook.delivery;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;

import javax.net.ssl.SSLHandshakeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "webhook-platform.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
class WebhookDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryWorker.class);

    private final WebhookDeliveryClaimService claims;
    private final WebhookDeliveryPayloadFactory payloadFactory;
    private final OutboundWebhookClient httpClient;
    private final WebhookDeliveryAttemptService attempts;
    private final WebhookWorkerProperties properties;

    WebhookDeliveryWorker(
            WebhookDeliveryClaimService claims,
            WebhookDeliveryPayloadFactory payloadFactory,
            OutboundWebhookClient httpClient,
            WebhookDeliveryAttemptService attempts,
            WebhookWorkerProperties properties) {
        this.claims = claims;
        this.payloadFactory = payloadFactory;
        this.httpClient = httpClient;
        this.attempts = attempts;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${webhook-platform.worker.poll-interval:PT5S}")
    void processPendingDeliveries() {
        claims.recoverStaleProcessing();
        processOnce();
    }

    void processOnce() {
        List<ClaimedDelivery> deliveries = claims.claimPending(properties.batchSize());
        for (ClaimedDelivery delivery : deliveries) {
            process(delivery);
        }
    }

    private void process(ClaimedDelivery delivery) {
        StartedWebhookDeliveryAttempt attempt = attempts.startAttempt(delivery).orElse(null);
        if (attempt == null) {
            return;
        }
        long startedNanos = System.nanoTime();
        boolean successful = false;
        Integer httpStatusCode = null;
        WebhookDeliveryAttemptErrorCode errorCode = null;
        try {
            httpStatusCode = httpClient.post(delivery, payloadFactory.create(delivery));
            successful = httpStatusCode >= 200 && httpStatusCode < 300;
            if (!successful) {
                errorCode = WebhookDeliveryAttemptErrorCode.HTTP_ERROR;
            }
        } catch (Exception exception) {
            errorCode = classify(exception);
            log.warn("Webhook delivery failed deliveryId={} eventId={} endpointId={} eventType={} error={}",
                    delivery.deliveryId(), delivery.eventId(), delivery.endpointId(), delivery.eventType(),
                    exception.getClass().getSimpleName());
        }
        long durationMs = Math.max(0, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
        boolean finalized = attempts.completeAttemptAndResolve(attempt, successful, httpStatusCode, errorCode, durationMs);
        if (finalized) {
            log.info("Webhook delivery completed deliveryId={} eventId={} endpointId={} eventType={} success={} durationMs={}",
                    delivery.deliveryId(), delivery.eventId(), delivery.endpointId(), delivery.eventType(), successful,
                    durationMs);
        }
    }

    private WebhookDeliveryAttemptErrorCode classify(Exception exception) {
        if (hasCause(exception, UnsafeWebhookDestinationException.class)) return WebhookDeliveryAttemptErrorCode.SSRF_REJECTED;
        if (hasCause(exception, UnknownHostException.class)) return WebhookDeliveryAttemptErrorCode.DNS_ERROR;
        if (hasCause(exception, SSLHandshakeException.class)) return WebhookDeliveryAttemptErrorCode.TLS_ERROR;
        if (hasCause(exception, SocketTimeoutException.class)) return WebhookDeliveryAttemptErrorCode.TIMEOUT;
        if (hasCause(exception, ConnectException.class)) return WebhookDeliveryAttemptErrorCode.CONNECTION_ERROR;
        if (hasCause(exception, java.io.IOException.class)) return WebhookDeliveryAttemptErrorCode.IO_ERROR;
        return WebhookDeliveryAttemptErrorCode.UNEXPECTED_ERROR;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }
}
