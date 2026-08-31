package com.webhookplatform.webhook.endpoint;

public class WebhookSigningSecretAlreadyProvisionedException extends RuntimeException {

    public WebhookSigningSecretAlreadyProvisionedException() {
        super("A signing secret is already provisioned for this endpoint.");
    }
}
