package com.webhookplatform.webhook.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public CurrentUser requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedOidcUser principal)) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated local user is required");
        }
        return new CurrentUser(
                principal.getLocalUserId(),
                principal.getLocalEmail(),
                principal.getLocalDisplayName(),
                principal.getLocalAvatarUrl());
    }
}
