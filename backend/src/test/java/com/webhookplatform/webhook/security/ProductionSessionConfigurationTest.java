package com.webhookplatform.webhook.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "GOOGLE_CLIENT_ID=test-client-id",
        "GOOGLE_CLIENT_SECRET=test-client-secret",
        "webhook-platform.frontend-url=https://webhook.example.test"
})
@ActiveProfiles("prod")
@Testcontainers
class ProductionSessionConfigurationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ServerProperties serverProperties;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Test
    void productionUsesSecureLaxHostOnlyCookieForRelatedFrontendAndApiDomains() {
        var cookie = serverProperties.getServlet().getSession().getCookie();

        assertThat(cookie.getName()).isEqualTo("WEBHOOK_SESSION");
        assertThat(cookie.getHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo(SameSite.LAX);
        assertThat(cookie.getDomain()).isNull();

        MockHttpServletRequest apiRequest = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        apiRequest.setServerName("api.webhook.example.test");
        CorsConfiguration cors = corsConfigurationSource.getCorsConfiguration(apiRequest);

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly("https://webhook.example.test");
        assertThat(cors.getAllowCredentials()).isTrue();
    }
}
