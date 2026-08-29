package com.webhookplatform.webhook.security;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.webhookplatform.webhook.user.User;

public final class AuthenticatedOidcUser implements OidcUser {

    private final OidcUser delegate;
    private final UUID localUserId;
    private final String email;
    private final String displayName;
    private final String avatarUrl;

    AuthenticatedOidcUser(OidcUser delegate, User user) {
        this(delegate, user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl());
    }

    public AuthenticatedOidcUser(
            OidcUser delegate,
            UUID localUserId,
            String email,
            String displayName,
            String avatarUrl) {
        this.delegate = delegate;
        this.localUserId = localUserId;
        this.email = email;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
    }

    public UUID getLocalUserId() {
        return localUserId;
    }

    public String getLocalEmail() {
        return email;
    }

    public String getLocalDisplayName() {
        return displayName;
    }

    public String getLocalAvatarUrl() {
        return avatarUrl;
    }

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}
