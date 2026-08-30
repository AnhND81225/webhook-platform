package com.webhookplatform.webhook.delivery;

import java.time.Instant;
import java.util.UUID;

record ClaimedDelivery(
        UUID deliveryId,
        UUID eventId,
        UUID endpointId,
        UUID claimToken,
        String targetUrl,
        String sourceEventId,
        String eventType,
        Instant eventCreatedAt,
        String payloadJson) {
}
