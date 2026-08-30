package com.webhookplatform.webhook.delivery;

import java.time.Duration;

record WebhookRetryDecision(Duration delay) {
    static WebhookRetryDecision terminal() {
        return new WebhookRetryDecision(null);
    }

    static WebhookRetryDecision retryAfter(Duration delay) {
        return new WebhookRetryDecision(delay);
    }

    boolean shouldRetry() {
        return delay != null;
    }
}
