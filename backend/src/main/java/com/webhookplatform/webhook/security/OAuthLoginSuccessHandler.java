package com.webhookplatform.webhook.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

final class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2AuthorizedClientRepository authorizedClientRepository;
    private final SimpleUrlAuthenticationSuccessHandler delegate;

    OAuthLoginSuccessHandler(OAuth2AuthorizedClientRepository authorizedClientRepository, String targetUrl) {
        this.authorizedClientRepository = authorizedClientRepository;
        this.delegate = new SimpleUrlAuthenticationSuccessHandler(targetUrl);
        this.delegate.setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        if (authentication instanceof OAuth2AuthenticationToken oauthAuthentication) {
            authorizedClientRepository.removeAuthorizedClient(
                    oauthAuthentication.getAuthorizedClientRegistrationId(),
                    oauthAuthentication,
                    request,
                    response);
        }
        delegate.onAuthenticationSuccess(request, response, authentication);
    }
}
