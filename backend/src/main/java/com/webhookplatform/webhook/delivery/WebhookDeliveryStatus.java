package com.webhookplatform.webhook.delivery;

public enum WebhookDeliveryStatus {
    PENDING,
    PROCESSING,
    RETRY_SCHEDULED,
    DELIVERED,
    FAILED
}
