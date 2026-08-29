package com.webhookplatform.webhook.subscription;

public class WebhookSubscriptionConflictException extends RuntimeException {

    public WebhookSubscriptionConflictException() {
        super("The endpoint is already subscribed to this event type.");
    }
}
