package com.webhookplatform.webhook.security;

import java.util.Map;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.webhookplatform.webhook.user.User;
import com.webhookplatform.webhook.user.UserService;

@Service
public class GoogleOidcUserService extends OidcUserService {

    private static final String GOOGLE_SUBJECT_CONSTRAINT = "uq_users_google_subject";

    private final UserService userService;

    public GoogleOidcUserService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        return provision(super.loadUser(userRequest));
    }

    AuthenticatedOidcUser provision(OidcUser oidcUser) {
        Map<String, Object> claims = oidcUser.getClaims();
        String subject = requiredStringClaim(claims, "sub");
        String email = requiredStringClaim(claims, "email");
        if (!Boolean.TRUE.equals(claims.get("email_verified"))) {
            throw authenticationFailure("unverified_email", "A verified Google email is required");
        }

        String displayName = optionalStringClaim(claims, "name");
        if (displayName == null) {
            displayName = email;
        }
        String avatarUrl = optionalStringClaim(claims, "picture");

        User user;
        try {
            user = userService.synchronizeGoogleUser(subject, email, displayName, avatarUrl);
        } catch (DataIntegrityViolationException exception) {
            if (!isGoogleSubjectConflict(exception)) {
                throw exception;
            }
            user = userService.synchronizeExistingGoogleUser(subject, email, displayName, avatarUrl);
        }
        return new AuthenticatedOidcUser(oidcUser, user);
    }

    private String requiredStringClaim(Map<String, Object> claims, String name) {
        String value = optionalStringClaim(claims, name);
        if (value == null) {
            throw authenticationFailure("invalid_identity", "Required identity claim is missing");
        }
        return value;
    }

    private String optionalStringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            return null;
        }
        return stringValue;
    }

    private boolean isGoogleSubjectConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && GOOGLE_SUBJECT_CONSTRAINT.equals(constraintViolation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private OAuth2AuthenticationException authenticationFailure(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code), description);
    }
}
