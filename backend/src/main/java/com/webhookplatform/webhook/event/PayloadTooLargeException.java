package com.webhookplatform.webhook.event;

import java.io.IOException;

public class PayloadTooLargeException extends IOException {

    public PayloadTooLargeException() {
        super("Producer request exceeds the 1 MiB limit.");
    }
}
