package com.webhookplatform.webhook.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.webhookplatform.webhook.user.User;
import com.webhookplatform.webhook.user.UserRepository;

@SpringBootTest(properties = {
        "GOOGLE_CLIENT_ID=test-client-id",
        "GOOGLE_CLIENT_SECRET=test-client-secret",
        "webhook-platform.frontend-url=http://localhost:5173",
        "webhook-platform.worker.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class M1AuthenticationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private GoogleOidcUserService googleOidcUserService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void deleteUsers() {
        userRepository.deleteAll();
    }

    @Test
    void firstLoginCreatesUserAndRepeatLoginSynchronizesProfile() {
        AuthenticatedOidcUser firstPrincipal = googleOidcUserService.provision(oidcUser(Map.of(
                "sub", "google-123",
                "email", "first@example.com",
                "email_verified", true,
                "name", "First Name",
                "picture", "https://example.com/first.png")));

        AuthenticatedOidcUser repeatPrincipal = googleOidcUserService.provision(oidcUser(Map.of(
                "sub", "google-123",
                "email", "changed@example.com",
                "email_verified", true,
                "name", "Changed Name",
                "picture", "https://example.com/changed.png")));

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(repeatPrincipal.getLocalUserId()).isEqualTo(firstPrincipal.getLocalUserId());
        User user = userRepository.findByGoogleSubject("google-123").orElseThrow();
        assertThat(user.getEmail()).isEqualTo("changed@example.com");
        assertThat(user.getDisplayName()).isEqualTo("Changed Name");
        assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/changed.png");
        assertThat(user.getLastLoginAt()).isAfterOrEqualTo(user.getCreatedAt());
    }

    @Test
    void differentGoogleSubjectsMayUseTheSameEmail() {
        googleOidcUserService.provision(oidcUser(validClaims("google-1", "shared@example.com")));
        googleOidcUserService.provision(oidcUser(validClaims("google-2", "shared@example.com")));

        assertThat(userRepository.count()).isEqualTo(2);
    }

    @Test
    void databaseEnforcesGoogleSubjectUniqueness() {
        insertUser("google-unique", "one@example.com");

        assertThatThrownBy(() -> insertUser("google-unique", "two@example.com"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void simultaneousFirstLoginsResolveToOneLocalUser() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<UUID> login = () -> {
                ready.countDown();
                start.await();
                return googleOidcUserService.provision(
                        oidcUser(validClaims("google-race", "race@example.com")))
                        .getLocalUserId();
            };
            Future<UUID> first = executor.submit(login);
            Future<UUID> second = executor.submit(login);
            ready.await();
            start.countDown();

            assertThat(first.get()).isEqualTo(second.get());
            assertThat(userRepository.findByGoogleSubject("google-race")).isPresent();
            assertThat(userRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void missingOrUnverifiedIdentityClaimsFailAuthentication() {
        Map<String, Object> missingSubject = Map.of(
                "test_name", "principal",
                "email", "developer@example.com",
                "email_verified", true);
        Map<String, Object> missingEmail = Map.of(
                "sub", "google-1",
                "test_name", "principal",
                "email_verified", true);
        Map<String, Object> unverifiedEmail = Map.of(
                "sub", "google-1",
                "test_name", "principal",
                "email", "developer@example.com",
                "email_verified", false);

        assertThatThrownBy(() -> googleOidcUserService.provision(oidcUser(missingSubject)))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(() -> googleOidcUserService.provision(oidcUser(missingEmail)))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(() -> googleOidcUserService.provision(oidcUser(unverifiedEmail)))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void missingNameFallsBackToEmailAndMissingPictureIsAllowed() {
        AuthenticatedOidcUser principal = googleOidcUserService.provision(oidcUser(Map.of(
                "sub", "google-fallback",
                "email", "fallback@example.com",
                "email_verified", true)));

        assertThat(principal.getLocalDisplayName()).isEqualTo("fallback@example.com");
        assertThat(principal.getLocalAvatarUrl()).isNull();
    }

    @Test
    void disabledLocalUserCannotLogIn() {
        googleOidcUserService.provision(oidcUser(validClaims("google-disabled", "disabled@example.com")));
        jdbcTemplate.update("UPDATE users SET status = 'DISABLED' WHERE google_subject = ?", "google-disabled");

        assertThatThrownBy(() -> googleOidcUserService.provision(
                oidcUser(validClaims("google-disabled", "disabled@example.com"))))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    void authenticatedMeReturnsOnlySafeLocalFields() throws Exception {
        AuthenticatedOidcUser principal = googleOidcUserService.provision(
                oidcUser(validClaims("google-api", "api@example.com")));
        OAuth2AuthenticationToken authentication = authenticationFor(principal);

        mockMvc.perform(get("/api/v1/auth/me").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(principal.getLocalUserId().toString()))
                .andExpect(jsonPath("$.email").value("api@example.com"))
                .andExpect(jsonPath("$.displayName").value("Developer"))
                .andExpect(jsonPath("$.googleSubject").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.idToken").doesNotExist())
                .andExpect(jsonPath("$.sessionId").doesNotExist());
    }

    @Test
    void oauthSuccessRemovesAuthorizedClientAndPreservesFixedLocalSession() throws Exception {
        AuthenticatedOidcUser principal = googleOidcUserService.provision(
                oidcUser(validClaims("google-success", "success@example.com")));
        OAuth2AuthenticationToken authentication = authenticationFor(principal);
        MockHttpSession session = authenticatedSession(authentication);
        OAuth2AuthorizedClientRepository authorizedClientRepository = mock(OAuth2AuthorizedClientRepository.class);
        OAuthLoginSuccessHandler successHandler = new OAuthLoginSuccessHandler(
                authorizedClientRepository,
                "https://webhook.example.com/app");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        request.setParameter("redirect_uri", "https://attacker.example/redirect");
        request.setParameter("targetUrl", "https://attacker.example/redirect");
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(request, response, authentication);

        verify(authorizedClientRepository).removeAuthorizedClient(
                "google",
                authentication,
                request,
                response);
        assertThat(response.getRedirectedUrl()).isEqualTo("https://webhook.example.com/app");
        assertThat(session.isInvalid()).isFalse();
        SecurityContext storedContext = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(storedContext.getAuthentication()).isSameAs(authentication);
        assertThat(storedContext.getAuthentication().getPrincipal()).isSameAs(principal);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(principal.getLocalUserId().toString()));
    }

    @Test
    void unauthenticatedApiReturnsJson401AndHealthRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void logoutRequiresCsrfAndReturnsNoContent() throws Exception {
        AuthenticatedOidcUser principal = googleOidcUserService.provision(
                oidcUser(validClaims("google-logout", "logout@example.com")));
        OAuth2AuthenticationToken authentication = authenticationFor(principal);
        MockHttpSession session = authenticatedSession(authentication);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("WEBHOOK_SESSION", 0))
                .andExpect(cookie().path("WEBHOOK_SESSION", "/"));

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedClientCanRetrieveCsrfToken() throws Exception {
        AuthenticatedOidcUser principal = googleOidcUserService.provision(
                oidcUser(validClaims("google-csrf", "csrf@example.com")));

        mockMvc.perform(get("/api/v1/auth/csrf").with(authentication(authenticationFor(principal))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void trustedOriginReceivesCredentialedCorsPreflightPermission() throws Exception {
        mockMvc.perform(options("/api/v1/auth/logout")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-CSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        containsString("POST")))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("X-CSRF-TOKEN")));
    }

    @Test
    void untrustedOriginReceivesNoCorsPermission() throws Exception {
        mockMvc.perform(options("/api/v1/auth/logout")
                        .header(HttpHeaders.ORIGIN, "https://attacker.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-CSRF-TOKEN"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void webhookBusinessApiIsNotImplemented() throws Exception {
        AuthenticatedOidcUser principal = googleOidcUserService.provision(
                oidcUser(validClaims("google-scope", "scope@example.com")));

        mockMvc.perform(get("/api/v1/events").with(authentication(authenticationFor(principal))))
                .andExpect(status().isForbidden());
    }

    private Map<String, Object> validClaims(String subject, String email) {
        return Map.of(
                "sub", subject,
                "email", email,
                "email_verified", true,
                "name", "Developer",
                "picture", "https://example.com/avatar.png");
    }

    private OidcUser oidcUser(Map<String, Object> suppliedClaims) {
        Map<String, Object> claims = new java.util.HashMap<>(suppliedClaims);
        claims.putIfAbsent("test_name", "principal");
        OidcIdToken idToken = new OidcIdToken(
                "backend-only-id-token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                claims);
        return new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                idToken,
                "test_name");
    }

    private OAuth2AuthenticationToken authenticationFor(AuthenticatedOidcUser principal) {
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }

    private MockHttpSession authenticatedSession(OAuth2AuthenticationToken authentication) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private void insertUser(String subject, String email) {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, google_subject, email, display_name, status,
                    last_login_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), subject, email, email);
    }
}
