package com.webhookplatform.webhook.security;

import java.util.UUID;

/**
 * Identity established exclusively by a valid producer API key.
 */
public record ProducerPrincipal(UUID apiKeyId, UUID applicationId) {
}
