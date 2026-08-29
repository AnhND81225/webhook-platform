package com.webhookplatform.webhook.security;

import java.util.UUID;

public record CurrentUser(UUID id, String email, String displayName, String avatarUrl) {
}
