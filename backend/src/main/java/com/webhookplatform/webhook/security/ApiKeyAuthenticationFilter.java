package com.webhookplatform.webhook.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.webhookplatform.webhook.apikey.ApiKeyAuthenticationService;
import com.webhookplatform.webhook.apikey.InvalidApiKeyException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String EVENTS_PATH = "/api/v1/events";

    private final ApiKeyAuthenticationService apiKeyAuthenticationService;
    private final ProducerAuthenticationEntryPoint authenticationEntryPoint;

    public ApiKeyAuthenticationFilter(
            ApiKeyAuthenticationService apiKeyAuthenticationService,
            ProducerAuthenticationEntryPoint authenticationEntryPoint) {
        this.apiKeyAuthenticationService = apiKeyAuthenticationService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !EVENTS_PATH.equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            ProducerPrincipal principal = apiKeyAuthenticationService.authenticate(extractBearerCredential(request));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_PRODUCER"))));
            SecurityContextHolder.setContext(context);
            filterChain.doFilter(request, response);
        } catch (InvalidApiKeyException exception) {
            authenticationEntryPoint.commence(request, response, null);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String extractBearerCredential(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders(HttpHeaders.AUTHORIZATION);
        List<String> headers = new ArrayList<>();
        while (values.hasMoreElements()) {
            headers.add(values.nextElement());
        }
        if (headers.size() != 1) {
            throw new InvalidApiKeyException();
        }

        String header = headers.get(0);
        if (header.length() <= 7 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new InvalidApiKeyException();
        }

        String credential = header.substring(7);
        if (credential.isBlank() || credential.chars().anyMatch(Character::isWhitespace)) {
            throw new InvalidApiKeyException();
        }
        return credential;
    }
}
