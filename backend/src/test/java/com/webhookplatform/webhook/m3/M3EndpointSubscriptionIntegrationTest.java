package com.webhookplatform.webhook.m3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.webhookplatform.webhook.security.AuthenticatedOidcUser;

@SpringBootTest(properties = {
        "GOOGLE_CLIENT_ID=test-client-id",
        "GOOGLE_CLIENT_SECRET=test-client-secret",
        "webhook-platform.frontend-url=http://localhost:5173"
})
@AutoConfigureMockMvc
@Testcontainers
class M3EndpointSubscriptionIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM webhook_subscriptions");
        jdbcTemplate.update("DELETE FROM webhook_endpoints");
        jdbcTemplate.update("DELETE FROM api_keys");
        jdbcTemplate.update("DELETE FROM applications");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void createsListsGetsAndPatchesOwnedEndpointsWithCsrf() throws Exception {
        OAuth2AuthenticationToken owner = authenticationFor(insertUser("endpoint-owner"));
        UUID applicationId = insertApplication(owner.getPrincipal().getName(), "owner-app");

        mockMvc.perform(post(endpointPath(applicationId)).with(authentication(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(endpointRequest("Analytics", "http://localhost:8081/hook")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        UUID endpointId = responseId(mockMvc.perform(post(endpointPath(applicationId)).with(authentication(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(endpointRequest("  Analytics  ", "http://localhost:8081/hook")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Analytics"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn());

        mockMvc.perform(get(endpointPath(applicationId)).with(authentication(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(patch(endpointPath(applicationId) + "/{id}", endpointId).with(authentication(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Updated \",\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"))
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    void rejectsInvalidEndpointUrlsAndUnknownFields() throws Exception {
        OAuth2AuthenticationToken owner = authenticationFor(insertUser("url-owner"));
        UUID applicationId = insertApplication(owner.getPrincipal().getName(), "url-app");

        for (String url : List.of("ftp://example.com/hook", "https://user:password@example.com/hook", "https://example.com/hook#x")) {
            mockMvc.perform(post(endpointPath(applicationId)).with(authentication(owner)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(endpointRequest("Endpoint", url)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ENDPOINT_URL"));
        }
        mockMvc.perform(post(endpointPath(applicationId)).with(authentication(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Endpoint\",\"url\":\"http://localhost/hook\",\"applicationId\":\"%s\"}"
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void enforcesOwnerScopedEndpointsAndSubscriptions() throws Exception {
        OAuth2AuthenticationToken owner = authenticationFor(insertUser("subscription-owner"));
        OAuth2AuthenticationToken other = authenticationFor(insertUser("subscription-other"));
        UUID applicationId = insertApplication(owner.getPrincipal().getName(), "subscription-app");
        UUID endpointId = createEndpoint(owner, applicationId, "Endpoint", "http://localhost:8081/hook");

        mockMvc.perform(get(endpointPath(applicationId) + "/{id}", endpointId).with(authentication(other)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
        mockMvc.perform(post(subscriptionPath(applicationId, endpointId)).with(authentication(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"eventType\":\"ai.solution.completed\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));

        UUID subscriptionId = responseId(mockMvc.perform(post(subscriptionPath(applicationId, endpointId)).with(authentication(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"eventType\":\"ai.solution.completed\"}"))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post(subscriptionPath(applicationId, endpointId)).with(authentication(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"eventType\":\"ai.solution.completed\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUBSCRIPTION_ALREADY_EXISTS"));
        mockMvc.perform(post(subscriptionPath(applicationId, endpointId)).with(authentication(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"eventType\":\"ai.grade.completed\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get(subscriptionPath(applicationId, endpointId)).with(authentication(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(delete(subscriptionPath(applicationId, endpointId) + "/{id}", subscriptionId)
                        .with(authentication(other)).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
        mockMvc.perform(delete(subscriptionPath(applicationId, endpointId) + "/{id}", subscriptionId)
                        .with(authentication(owner)).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void disabledEndpointKeepsSubscriptionsAndV3ConstraintsAreEnforced() throws Exception {
        OAuth2AuthenticationToken owner = authenticationFor(insertUser("constraint-owner"));
        UUID applicationId = insertApplication(owner.getPrincipal().getName(), "constraint-app");
        UUID endpointId = createEndpoint(owner, applicationId, "Endpoint", "http://localhost:8081/hook");
        createSubscription(owner, applicationId, endpointId, "ai.answer.reported");
        mockMvc.perform(patch(endpointPath(applicationId) + "/{id}", endpointId).with(authentication(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_subscriptions WHERE endpoint_id = ?", Long.class, endpointId))
                .isEqualTo(1L);

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM applications WHERE id = ?", applicationId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO webhook_subscriptions (id, endpoint_id, event_type, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)", UUID.randomUUID(), endpointId, "bad"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class))
                .containsExactly("1", "2", "3", "4", "5");
    }

    private UUID insertUser(String subject) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, google_subject, email, display_name, status, last_login_at, created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, subject, subject + "@example.com", subject);
        return id;
    }

    private UUID insertApplication(String ownerId, String slug) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO applications (id, owner_user_id, name, slug, status, environment, created_at, updated_at) VALUES (?, ?, 'App', ?, 'ACTIVE', 'DEVELOPMENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, UUID.fromString(ownerId), slug);
        return id;
    }

    private UUID createEndpoint(OAuth2AuthenticationToken authentication, UUID applicationId, String name, String url) throws Exception {
        return responseId(mockMvc.perform(post(endpointPath(applicationId)).with(authentication(authentication)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(endpointRequest(name, url)))
                .andExpect(status().isCreated()).andReturn());
    }

    private void createSubscription(OAuth2AuthenticationToken authentication, UUID applicationId, UUID endpointId, String eventType) throws Exception {
        mockMvc.perform(post(subscriptionPath(applicationId, endpointId)).with(authentication(authentication)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"eventType\":\"" + eventType + "\"}"))
                .andExpect(status().isCreated());
    }

    private String endpointPath(UUID applicationId) { return "/api/v1/applications/" + applicationId + "/endpoints"; }
    private String subscriptionPath(UUID applicationId, UUID endpointId) { return endpointPath(applicationId) + "/" + endpointId + "/subscriptions"; }
    private String endpointRequest(String name, String url) throws Exception { return objectMapper.writeValueAsString(java.util.Map.of("name", name, "url", url)); }
    private UUID responseId(MvcResult result) throws Exception { return UUID.fromString(response(result).get("id").asText()); }
    private JsonNode response(MvcResult result) throws Exception { return objectMapper.readTree(result.getResponse().getContentAsString()); }

    private OAuth2AuthenticationToken authenticationFor(UUID userId) {
        OidcUser delegate = mock(OidcUser.class);
        Collection<? extends GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        doReturn(authorities).when(delegate).getAuthorities();
        when(delegate.getName()).thenReturn(userId.toString());
        return new OAuth2AuthenticationToken(new AuthenticatedOidcUser(delegate, userId, userId + "@example.com", "Developer", null), authorities, "google");
    }
}
