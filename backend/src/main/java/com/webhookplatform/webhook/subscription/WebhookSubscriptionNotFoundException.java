package com.webhookplatform.webhook.subscription;

public class WebhookSubscriptionNotFoundException extends RuntimeException {

    public WebhookSubscriptionNotFoundException() {
        super("Webhook subscription was not found.");
    }
}
