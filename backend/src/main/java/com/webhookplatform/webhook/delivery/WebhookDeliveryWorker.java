package com.webhookplatform.webhook.delivery;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

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
    private final WebhookWorkerProperties properties;
    private final Clock clock;

    WebhookDeliveryWorker(
            WebhookDeliveryClaimService claims,
            WebhookDeliveryPayloadFactory payloadFactory,
            OutboundWebhookClient httpClient,
            WebhookWorkerProperties properties,
            Clock clock) {
        this.claims = claims;
        this.payloadFactory = payloadFactory;
        this.httpClient = httpClient;
        this.properties = properties;
        this.clock = clock;
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
        Instant startedAt = clock.instant();
        WebhookDeliveryStatus finalStatus = WebhookDeliveryStatus.FAILED;
        try {
            int status = httpClient.post(delivery, payloadFactory.create(delivery));
            finalStatus = status >= 200 && status < 300
                    ? WebhookDeliveryStatus.DELIVERED
                    : WebhookDeliveryStatus.FAILED;
        } catch (Exception exception) {
            log.warn("Webhook delivery failed deliveryId={} eventId={} endpointId={} eventType={} error={}",
                    delivery.deliveryId(), delivery.eventId(), delivery.endpointId(), delivery.eventType(),
                    exception.getClass().getSimpleName());
        }
        boolean finalized = claims.finalizeClaim(delivery.deliveryId(), delivery.claimToken(), finalStatus);
        if (finalized) {
            log.info("Webhook delivery finalized deliveryId={} eventId={} endpointId={} eventType={} status={} durationMs={}",
                    delivery.deliveryId(), delivery.eventId(), delivery.endpointId(), delivery.eventType(), finalStatus,
                    java.time.Duration.between(startedAt, clock.instant()).toMillis());
        }
    }
}
