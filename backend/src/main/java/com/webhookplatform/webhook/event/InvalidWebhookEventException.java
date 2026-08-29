package com.webhookplatform.webhook.event;

public class InvalidWebhookEventException extends RuntimeException {

    public InvalidWebhookEventException(String message) {
        super(message);
    }
}
