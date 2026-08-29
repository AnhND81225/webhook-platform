package com.webhookplatform.webhook.auth;

import java.util.UUID;

import com.webhookplatform.webhook.security.CurrentUser;

public record CurrentUserResponse(UUID id, String email, String displayName, String avatarUrl) {

    static CurrentUserResponse from(CurrentUser currentUser) {
        return new CurrentUserResponse(
                currentUser.id(),
                currentUser.email(),
                currentUser.displayName(),
                currentUser.avatarUrl());
    }
}
