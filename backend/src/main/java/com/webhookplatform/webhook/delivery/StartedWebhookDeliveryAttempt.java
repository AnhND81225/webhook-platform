package com.webhookplatform.webhook.delivery;

import java.util.UUID;

record StartedWebhookDeliveryAttempt(UUID attemptId, UUID deliveryId, UUID claimToken, int attemptNumber) {
}
