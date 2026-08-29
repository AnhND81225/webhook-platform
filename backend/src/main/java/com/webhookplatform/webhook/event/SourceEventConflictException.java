package com.webhookplatform.webhook.event;

public class SourceEventConflictException extends RuntimeException {

    public SourceEventConflictException() {
        super("sourceEventId is already associated with a different event.");
    }
}
