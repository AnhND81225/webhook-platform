package com.webhookplatform.webhook.m4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.List;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhookplatform.webhook.security.AuthenticatedOidcUser;

@SpringBootTest(properties = {
        "GOOGLE_CLIENT_ID=test-client-id",
        "GOOGLE_CLIENT_SECRET=test-client-secret",
        "webhook-platform.frontend-url=http://localhost:5173"
})
@AutoConfigureMockMvc
@Testcontainers
class M4EventIngestionIntegrationTest {

    private static final String EVENTS_PATH = "/api/v1/events";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM webhook_events");
        jdbcTemplate.update("DELETE FROM webhook_subscriptions");
        jdbcTemplate.update("DELETE FROM webhook_endpoints");
        jdbcTemplate.update("DELETE FROM api_keys");
        jdbcTemplate.update("DELETE FROM applications");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void authenticatesAnActiveKeyWithoutCsrfAndPersistsAnImmutableJsonbEvent() throws Exception {
        OAuth2AuthenticationToken owner = authenticationFor(insertUser("event-owner"));
        UUID applicationId = insertApplication(owner.getPrincipal().getName(), "event-app");
        String rawKey = createApiKey(owner, applicationId);

        MvcResult result = ingest(rawKey, eventRequest("solution-1", "ai.solution.completed", "{\"solutionId\":\"1\",\"status\":\"completed\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceEventId").value("solution-1"))
                .andExpect(jsonPath("$.eventType").value("ai.solution.completed"))
                .andExpect(jsonPath("$.payload").doesNotExist())
                .andExpect(jsonPath("$.applicationId").doesNotExist())
                .andReturn();

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_events WHERE application_id = ?", Long.class, applicationId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT payload::text FROM webhook_events", String.class))
                .contains("solutionId");
        assertThat(lastUsedAt(rawKey)).isNotNull();
        assertThat(response(result).get("id").asText()).isNotBlank();
    }

    @Test
    void rejectsCredentialsGenericallyAndDoesNotAllowDashboardOrProducerCredentialsAcrossBoundaries() throws Exception {
        OAuth2AuthenticationToken owner = authenticationFor(insertUser("security-owner"));
        UUID applicationId = insertApplication(owner.getPrincipal().getName(), "security-app");
        String rawKey = createApiKey(owner, applicationId);
        String request = eventRequest("event-1", "ai.solution.completed", "{}");

        mockMvc.perform(post(EVENTS_PATH).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_API_KEY"));
        mockMvc.perform(post(EVENTS_PATH).header("Authorization", "Basic nope").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_API_KEY"));
        mockMvc.perform(post(EVENTS_PATH).header("Authorization", "Bearer unknown").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_API_KEY"));
        mockMvc.perform(post(EVENTS_PATH).with(authentication(owner)).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_API_KEY"));
        mockMvc.perform(get("/api/v1/applications").header("Authorization", "Bearer " + rawKey))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(post("/api/v1/applications").with(authentication(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"App\",\"slug\":\"needs-csrf\",\"environment\":\"DEVELOPMENT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsRevokedKeysAndDisabledApplicationsWithoutUpdatingLastUsedTime() throws Exception {
        OAuth2AuthenticationToken owner = authenticationFor(insertUser("status-owner"));
        UUID revokedApplication = insertApplication(owner.getPrincipal().getName(), "revoked-app");
        String revokedKey = createApiKey(owner, revokedApplication);
        UUID revokedKeyId = keyId(revokedKey);
        jdbcTemplate.update("UPDATE api_keys SET status = 'REVOKED', revoked_at = CURRENT_TIMESTAMP WHERE id = ?", revokedKeyId);
        mockMvc.perform(post(EVENTS_PATH).header("Authorization", "Bearer " + revokedKey)
                        .contentType(MediaType.APPLICATION_JSON).content(eventRequest("revoked", "ai.solution.completed", "{}")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_API_KEY"));
        assertThat(lastUsedAt(revokedKey)).isNull();

        UUID disabledApplication = insertApplication(owner.getPrincipal().getName(), "disabled-app");
        String disabledKey = createApiKey(owner, disabledApplication);
        jdbcTemplate.update("UPDATE applications SET status = 'DISABLED' WHERE id = ?", disabledApplication);
        mockMvc.perform(post(EVENTS_PATH).header("Authorization", "Bearer " + disabledKey)
                        .contentType(MediaType.APPLICATION_JSON).content(eventRequest("disabled", "ai.solution.completed", "{}")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_API_KEY"));
        assertThat(lastUsedAt(disabledKey)).isNull();
    }

    @Test
    void validatesEventFieldsAndRecordsAValidKeyUseEvenWhenEventValidationFails() throws Exception {
        OAuth2AuthenticationToken owner = authenticationFor(insertUser("validation-owner"));
        UUID applicationId = insertApplication(owner.getPrincipal().getName(), "validation-app");
        String rawKey = createApiKey(owner, applicationId);

        mockMvc.perform(post(EVENTS_PATH).header("Authorization", "Bearer " + rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceEventId\":\"x\",\"eventType\":\"Bad\",\"payload\":[]}"))
                .andExpect(status().isBadRequest());
        assertThat(lastUsedAt(rawKey)).isNotNull();

        for (String payload : List.of("[]", "true", "null")) {
            mockMvc.perform(post(EVENTS_PATH).header("Authorization", "Bearer " + rawKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(eventRequest("payload-" + payload.replaceAll("[^a-z]", "x"), "ai.grade.completed", payload)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EVENT_REQUEST"));
        }
        mockMvc.perform(post(EVENTS_PATH).header("Authorization", "Bearer " + rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceEventId\":\"unknown\",\"eventType\":\"ai.grade.completed\",\"payload\":{},\"applicationId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void returnsExistingEventForJsonbEquivalentReplayAndRejectsConflictingReuse() throws Exception {
        OAuth2AuthenticationToken owner = authenticationFor(insertUser("idempotency-owner"));
        UUID applicationId = insertApplication(owner.getPrincipal().getName(), "idempotency-app");
        String rawKey = createApiKey(owner, applicationId);

        MvcResult first = ingest(rawKey, eventRequest("same-source", "ai.solution.completed", "{\"a\":1,\"b\":2}"))
                .andExpect(status().isCreated()).andReturn();
        ingest(rawKey, eventRequest("same-source", "ai.solution.completed", "{\"b\":2,\"a\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(response(first).get("id").asText()));
        ingest(rawKey, eventRequest("same-source", "ai.grade.completed", "{\"a\":1,\"b\":2}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SOURCE_EVENT_ID_CONFLICT"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_events", Long.class)).isEqualTo(1L);
    }

    @Test
    void rejectsAChangedPayloadForTheSameApplicationSourceEventAndEventType() throws Exception {
        OAuth2AuthenticationToken owner = authenticationFor(insertUser("payload-conflict-owner"));
        UUID applicationId = insertApplication(owner.getPrincipal().getName(), "payload-conflict-app");
        String rawKey = createApiKey(owner, applicationId);

        ingest(rawKey, eventRequest("solution-93482", "ai.solution.completed", "{\"status\":\"completed\"}"))
                .andExpect(status().isCreated());
        ingest(rawKey, eventRequest("solution-93482", "ai.solution.completed", "{\"status\":\"failed\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOURCE_EVENT_ID_CONFLICT"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_events", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT payload->>'status' FROM webhook_events", String.class))
                .isEqualTo("completed");
    }

    @Test
    void letsDifferentApplicationsReuseSourceIdsAndEnforcesV4Constraints() throws Exception {
        OAuth2AuthenticationToken firstOwner = authenticationFor(insertUser("first-event-owner"));
        OAuth2AuthenticationToken secondOwner = authenticationFor(insertUser("second-event-owner"));
        UUID firstApplication = insertApplication(firstOwner.getPrincipal().getName(), "first-event-app");
        UUID secondApplication = insertApplication(secondOwner.getPrincipal().getName(), "second-event-app");
        ingest(createApiKey(firstOwner, firstApplication), eventRequest("shared", "ai.answer.reported", "{}"))
                .andExpect(status().isCreated());
        ingest(createApiKey(secondOwner, secondApplication), eventRequest("shared", "ai.answer.reported", "{}"))
                .andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_events", Long.class)).isEqualTo(2L);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO webhook_events (id, application_id, source_event_id, event_type, payload, created_at) VALUES (?, ?, 'bad', 'bad', '{}'::jsonb, CURRENT_TIMESTAMP)", UUID.randomUUID(), firstApplication))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class))
                .containsExactly("1", "2", "3", "4");
    }

    @Test
    void limitsPayloadsAndConcurrentRetriesCreateOneEvent() throws Exception {
        OAuth2AuthenticationToken owner = authenticationFor(insertUser("concurrent-owner"));
        UUID applicationId = insertApplication(owner.getPrincipal().getName(), "concurrent-app");
        String rawKey = createApiKey(owner, applicationId);
        String boundaryTemplate = eventRequest("boundary", "ai.solution.completed", "{\"data\":\"\"}");
        String atLimit = eventRequest("boundary", "ai.solution.completed", "{\"data\":\""
                + "x".repeat(1024 * 1024 - boundaryTemplate.getBytes(StandardCharsets.UTF_8).length) + "\"}");
        assertThat(atLimit.getBytes(StandardCharsets.UTF_8)).hasSize(1024 * 1024);
        ingest(rawKey, atLimit).andExpect(status().isCreated());

        String oversized = eventRequest("large", "ai.solution.completed", "{\"data\":\"" + "x".repeat(1024 * 1024) + "\"}");
        mockMvc.perform(post(EVENTS_PATH).header("Authorization", "Bearer " + rawKey)
                        .contentType(MediaType.APPLICATION_JSON).content(oversized.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));

        String request = eventRequest("race", "ai.solution.completed", "{\"same\":true}");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<Integer> first = executor.submit(() -> concurrentIngest(rawKey, request, ready, start));
            Future<Integer> second = executor.submit(() -> concurrentIngest(rawKey, request, ready, start));
            ready.await();
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 200);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_events WHERE source_event_id = 'race'", Long.class))
                .isEqualTo(1L);
    }

    private int concurrentIngest(String rawKey, String request, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(post(EVENTS_PATH).header("Authorization", "Bearer " + rawKey)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andReturn().getResponse().getStatus();
    }

    private org.springframework.test.web.servlet.ResultActions ingest(String rawKey, String request) throws Exception {
        return mockMvc.perform(post(EVENTS_PATH).header("Authorization", "Bearer " + rawKey)
                .contentType(MediaType.APPLICATION_JSON).content(request));
    }

    private String eventRequest(String sourceEventId, String eventType, String payload) {
        return "{\"sourceEventId\":\"%s\",\"eventType\":\"%s\",\"payload\":%s}"
                .formatted(sourceEventId, eventType, payload);
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

    private String createApiKey(OAuth2AuthenticationToken owner, UUID applicationId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/applications/{id}/api-keys", applicationId)
                        .with(authentication(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Producer\"}"))
                .andExpect(status().isCreated()).andReturn();
        return response(result).get("apiKey").asText();
    }

    private UUID keyId(String rawKey) {
        return jdbcTemplate.queryForObject("SELECT id FROM api_keys WHERE key_hash = ?", UUID.class, keyHash(rawKey));
    }

    private java.time.Instant lastUsedAt(String rawKey) {
        return jdbcTemplate.queryForObject("SELECT last_used_at FROM api_keys WHERE key_hash = ?", java.time.Instant.class, keyHash(rawKey));
    }

    private String keyHash(String rawKey) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private JsonNode response(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private OAuth2AuthenticationToken authenticationFor(UUID userId) {
        OidcUser delegate = mock(OidcUser.class);
        Collection<? extends GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        doReturn(authorities).when(delegate).getAuthorities();
        when(delegate.getName()).thenReturn(userId.toString());
        return new OAuth2AuthenticationToken(new AuthenticatedOidcUser(delegate, userId, userId + "@example.com", "Developer", null), authorities, "google");
    }
}
