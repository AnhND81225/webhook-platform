package com.webhookplatform.webhook.delivery;

import org.springframework.stereotype.Component;

/** Centralizes M8 retry classification and bounded backoff. */
@Component
class WebhookRetryPolicy {

    private final WebhookRetryProperties properties;

    WebhookRetryPolicy(WebhookRetryProperties properties) {
        this.properties = properties;
    }

    WebhookRetryDecision forFailure(int attemptNumber, Integer httpStatusCode,
            WebhookDeliveryAttemptErrorCode errorCode) {
        if (!properties.enabled() || attemptNumber >= properties.maxAttempts()) {
            return WebhookRetryDecision.terminal();
        }
        if (httpStatusCode != null) {
            return retryableHttpStatus(httpStatusCode) ? retryFor(attemptNumber) : WebhookRetryDecision.terminal();
        }
        return isRetryable(errorCode) ? retryFor(attemptNumber) : WebhookRetryDecision.terminal();
    }

    WebhookRetryDecision forAbandonedAttempt(int attemptNumber) {
        return !properties.enabled() || attemptNumber >= properties.maxAttempts()
                ? WebhookRetryDecision.terminal()
                : retryFor(attemptNumber);
    }

    private WebhookRetryDecision retryFor(int attemptNumber) {
        return WebhookRetryDecision.retryAfter(properties.delays().get(attemptNumber - 1));
    }

    private boolean retryableHttpStatus(int status) {
        return status == 408 || status == 429 || (status >= 500 && status <= 599);
    }

    private boolean isRetryable(WebhookDeliveryAttemptErrorCode errorCode) {
        return errorCode == WebhookDeliveryAttemptErrorCode.DNS_ERROR
                || errorCode == WebhookDeliveryAttemptErrorCode.CONNECTION_ERROR
                || errorCode == WebhookDeliveryAttemptErrorCode.TIMEOUT;
    }
}
