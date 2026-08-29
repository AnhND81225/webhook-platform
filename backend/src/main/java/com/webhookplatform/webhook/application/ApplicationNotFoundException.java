package com.webhookplatform.webhook.application;

public class ApplicationNotFoundException extends RuntimeException {

    public ApplicationNotFoundException() {
        super("Application was not found.");
    }
}
