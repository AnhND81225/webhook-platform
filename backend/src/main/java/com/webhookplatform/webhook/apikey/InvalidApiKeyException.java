package com.webhookplatform.webhook.apikey;

/**
 * Intentionally contains no credential-state detail so producer failures remain generic.
 */
public class InvalidApiKeyException extends RuntimeException {

    public InvalidApiKeyException() {
        super("Producer API key is invalid.");
    }
}
