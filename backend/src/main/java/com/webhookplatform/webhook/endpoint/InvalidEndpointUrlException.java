package com.webhookplatform.webhook.endpoint;

public class InvalidEndpointUrlException extends RuntimeException {

    public InvalidEndpointUrlException() {
        super("Webhook endpoint URL is not allowed.");
    }
}
