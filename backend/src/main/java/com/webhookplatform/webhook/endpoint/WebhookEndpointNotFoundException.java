package com.webhookplatform.webhook.endpoint;

public class WebhookEndpointNotFoundException extends RuntimeException {

    public WebhookEndpointNotFoundException() {
        super("Webhook endpoint was not found.");
    }
}
