package com.webhookplatform.webhook.apikey;

public class ApiKeyNotFoundException extends RuntimeException {

    public ApiKeyNotFoundException() {
        super("API key was not found.");
    }
}
