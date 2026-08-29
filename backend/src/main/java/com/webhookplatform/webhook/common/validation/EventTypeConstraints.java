package com.webhookplatform.webhook.common.validation;

/**
 * Event types are lower-case dotted identifiers shared by subscriptions and producer events.
 */
public final class EventTypeConstraints {

    public static final String PATTERN = "^[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)+$";

    private EventTypeConstraints() {
    }
}
