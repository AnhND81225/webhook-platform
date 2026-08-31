package com.webhookplatform.webhook.delivery;

public enum WebhookDeliveryAttemptErrorCode {
    HTTP_ERROR,
    DNS_ERROR,
    SSRF_REJECTED,
    CONNECTION_ERROR,
    TIMEOUT,
    TLS_ERROR,
    SIGNING_ERROR,
    IO_ERROR,
    UNEXPECTED_ERROR
}
