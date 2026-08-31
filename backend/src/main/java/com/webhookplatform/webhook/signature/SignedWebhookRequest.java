package com.webhookplatform.webhook.signature;

public record SignedWebhookRequest(String timestamp, String signature) { }
