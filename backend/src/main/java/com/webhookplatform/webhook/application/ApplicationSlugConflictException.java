package com.webhookplatform.webhook.application;

public class ApplicationSlugConflictException extends RuntimeException {

    public ApplicationSlugConflictException() {
        super("An Application with this slug already exists.");
    }
}
