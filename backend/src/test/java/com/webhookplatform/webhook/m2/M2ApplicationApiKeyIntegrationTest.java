package com.webhookplatform.webhook.m2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

import com.webhookplatform.webhook.application.ApplicationEnvironment;
import com.webhookplatform.webhook.application.ApplicationRepository;
import com.webhookplatform.webhook.application.ApplicationService;
import com.webhookplatform.webhook.application.ApplicationSlugConflictException;
import com.webhookplatform.webhook.application.CreateApplicationRequest;
import com.webhookplatform.webhook.apikey.ApiKeyRepository;
import com.webhookplatform.webhook.security.AuthenticatedOidcUser;

@SpringBootTest(properties = {
        "GOOGLE_CLIENT_ID=test-client-id",
        "GOOGLE_CLIENT_SECRET=test-client-secret",
        "webhook-platform.frontend-url=http://localhost:5173",
        "webhook-platform.worker.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class M2ApplicationApiKeyIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApplicationService applicationService;

    @BeforeEach
    void cleanDatabase() {
        apiKeyRepository.deleteAllInBatch();
        applicationRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void createsOwnedApplicationWithDefaultsAndRejectsUnauthenticatedOrCsrflessRequests() throws Exception {
        UUID owner = insertUser("owner-create");
        OAuth2AuthenticationToken ownerAuthentication = authenticationFor(owner);
        String request = applicationRequest("  AI Study Assistant  ", "ai-study-assistant", "PRODUCTION");

        mockMvc.perform(post("/api/v1/applications").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(post("/api/v1/applications").with(authentication(ownerAuthentication))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        MvcResult result = mockMvc.perform(post("/api/v1/applications")
                        .with(authentication(ownerAuthentication)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/applications/")))
                .andExpect(jsonPath("$.name").value("AI Study Assistant"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.environment").value("PRODUCTION"))
                .andExpect(jsonPath("$.ownerUserId").doesNotExist())
                .andReturn();

        UUID applicationId = responseId(result);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT owner_user_id FROM applications WHERE id = ?", UUID.class, applicationId))
                .isEqualTo(owner);
    }

    @Test
    void validatesApplicationInputAndRejectsUnknownOwnershipOrImmutableFields() throws Exception {
        OAuth2AuthenticationToken authentication = authenticationFor(insertUser("owner-validation"));

        mockMvc.perform(post("/api/v1/applications").with(authentication(authentication)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationRequest("   ", "Invalid_Slug", "PRODUCTION")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/applications").with(authentication(authentication)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"App","slug":"app","environment":"PRODUCTION","ownerUserId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        UUID applicationId = createApplication(authentication, "App", "app", "PRODUCTION");
        mockMvc.perform(patch("/api/v1/applications/{id}", applicationId)
                        .with(authentication(authentication)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"slug\":\"changed\"}"))
                .andExpect(status().isBadRequest());

        assertThat(applicationRepository.findById(applicationId).orElseThrow().getSlug()).isEqualTo("app");
    }

    @Test
    void enforcesOwnerScopedSlugUniquenessAndAllowsSameSlugForDifferentOwners() throws Exception {
        OAuth2AuthenticationToken firstOwner = authenticationFor(insertUser("owner-slug-1"));
        OAuth2AuthenticationToken secondOwner = authenticationFor(insertUser("owner-slug-2"));

        createApplication(firstOwner, "First", "shared", "PRODUCTION");
        mockMvc.perform(post("/api/v1/applications").with(authentication(firstOwner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationRequest("Duplicate", "shared", "PRODUCTION")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_SLUG_CONFLICT"));

        createApplication(secondOwner, "Second", "shared", "DEVELOPMENT");
        assertThat(applicationRepository.count()).isEqualTo(2);
    }

    @Test
    void databaseConstraintResolvesConcurrentSameOwnerSlugCreation() throws Exception {
        UUID owner = insertUser("owner-race");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Object> create = () -> {
                ready.countDown();
                start.await();
                try {
                    return applicationService.create(owner,
                            new CreateApplicationRequest("Race", "race", ApplicationEnvironment.PRODUCTION));
                } catch (ApplicationSlugConflictException exception) {
                    return exception;
                }
            };
            Future<Object> first = executor.submit(create);
            Future<Object> second = executor.submit(create);
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .filteredOn(ApplicationSlugConflictException.class::isInstance)
                    .hasSize(1);
            assertThat(applicationRepository.findAllByOwnerUserIdOrderByCreatedAtDescIdDesc(owner)).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void listsReadsAndUpdatesOnlyOwnedApplicationsInStableOrder() throws Exception {
        OAuth2AuthenticationToken owner = authenticationFor(insertUser("owner-query"));
        OAuth2AuthenticationToken other = authenticationFor(insertUser("owner-other"));
        UUID first = createApplication(owner, "First", "first", "DEVELOPMENT");
        Thread.sleep(2);
        UUID second = createApplication(owner, "Second", "second", "PRODUCTION");
        createApplication(other, "Hidden", "hidden", "PRODUCTION");

        mockMvc.perform(get("/api/v1/applications").with(authentication(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second.toString()))
                .andExpect(jsonPath("$[1].id").value(first.toString()));

        mockMvc.perform(get("/api/v1/applications/{id}", second).with(authentication(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Second"));

        mockMvc.perform(get("/api/v1/applications/{id}", second).with(authentication(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));

        mockMvc.perform(patch("/api/v1/applications/{id}", second)
                        .with(authentication(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Renamed  \",\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.slug").value("second"))
                .andExpect(jsonPath("$.environment").value("PRODUCTION"));

        mockMvc.perform(patch("/api/v1/applications/{id}", second)
                        .with(authentication(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsRevealOnceProductionKeyAndPersistsOnlyHashAndPrefix() throws Exception {
        OAuth2AuthenticationToken owner = authenticationFor(insertUser("owner-key"));
        UUID applicationId = createApplication(owner, "Producer", "producer", "PRODUCTION");

        MvcResult result = createApiKey(owner, applicationId, "  Production  ");
        JsonNode response = response(result);
        UUID apiKeyId = UUID.fromString(response.get("id").asText());
        String rawKey = response.get("apiKey").asText();
        String expectedHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8)));

        assertThat(rawKey).startsWith("whk_live_").hasSize(52);
        assertThat(response.get("keyPrefix").asText()).isEqualTo(rawKey.substring(0, 13));
        assertThat(response.get("name").asText()).isEqualTo("Production");
        assertThat(response.get("lastUsedAt").isNull()).isTrue();
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");

        var row = jdbcTemplate.queryForMap(
                "SELECT key_prefix, key_hash, last_used_at FROM api_keys WHERE id = ?", apiKeyId);
        assertThat(row.get("key_prefix")).isEqualTo(rawKey.substring(0, 13));
        assertThat(row.get("key_hash")).isEqualTo(expectedHash);
        assertThat(row.get("last_used_at")).isNull();
        assertThat(row.values()).doesNotContain(rawKey);
    }

    @Test
    void developmentKeysUseTestMarkerAndNamesNeedNotBeUnique() throws Exception {
        OAuth2AuthenticationToken owner = authenticationFor(insertUser("owner-dev-key"));
        UUID applicationId = createApplication(owner, "Dev", "dev", "DEVELOPMENT");

        assertThat(response(createApiKey(owner, applicationId, "Local")).get("apiKey").asText())
                .startsWith("whk_test_");
        assertThat(response(createApiKey(owner, applicationId, "Local")).get("apiKey").asText())
                .startsWith("whk_test_");
        assertThat(apiKeyRepository.count()).isEqualTo(2);
    }

    @Test
    void listsOnlySafeKeyMetadataForOwnedApplication() throws Exception {
        OAuth2AuthenticationToken owner = authenticationFor(insertUser("owner-list-key"));
        OAuth2AuthenticationToken other = authenticationFor(insertUser("owner-list-other"));
        UUID applicationId = createApplication(owner, "Keys", "keys", "PRODUCTION");
        String rawKey = response(createApiKey(owner, applicationId, "First")).get("apiKey").asText();
        Thread.sleep(2);
        UUID secondId = UUID.fromString(response(createApiKey(owner, applicationId, "Second")).get("id").asText());

        MvcResult result = mockMvc.perform(get("/api/v1/applications/{id}/api-keys", applicationId)
                        .with(authentication(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(secondId.toString()))
                .andExpect(jsonPath("$[0].apiKey").doesNotExist())
                .andExpect(jsonPath("$[0].rawKey").doesNotExist())
                .andExpect(jsonPath("$[0].keyHash").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(rawKey);
        mockMvc.perform(get("/api/v1/applications/{id}/api-keys", applicationId)
                        .with(authentication(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
    }

    @Test
    void rejectsCrossUserKeyCreationAndRevocation() throws Exception {
        OAuth2AuthenticationToken owner = authenticationFor(insertUser("owner-cross-key"));
        OAuth2AuthenticationToken other = authenticationFor(insertUser("other-cross-key"));
        UUID applicationId = createApplication(owner, "Owned", "owned", "PRODUCTION");

        mockMvc.perform(post("/api/v1/applications/{id}/api-keys", applicationId)
                        .with(authentication(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nope\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));

        UUID keyId = UUID.fromString(response(createApiKey(owner, applicationId, "Owned key")).get("id").asText());
        mockMvc.perform(post("/api/v1/api-keys/{id}/revoke", keyId)
                        .with(authentication(other)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("API_KEY_NOT_FOUND"));
    }

    @Test
    void revocationIsIrreversibleAndIdempotent() throws Exception {
        OAuth2AuthenticationToken owner = authenticationFor(insertUser("owner-revoke"));
        UUID applicationId = createApplication(owner, "Revoke", "revoke", "PRODUCTION");
        UUID keyId = UUID.fromString(response(createApiKey(owner, applicationId, "Key")).get("id").asText());

        MvcResult first = mockMvc.perform(post("/api/v1/api-keys/{id}/revoke", keyId)
                        .with(authentication(owner)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.revokedAt").isNotEmpty())
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andReturn();
        String revokedAt = response(first).get("revokedAt").asText();

        mockMvc.perform(post("/api/v1/api-keys/{id}/revoke", keyId)
                        .with(authentication(owner)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.revokedAt").value(revokedAt));
    }

    @Test
    void postgresEnforcesForeignKeysEnumsHashesRevocationAndDeleteRestrictions() {
        UUID owner = insertUser("owner-constraints");
        UUID applicationId = insertApplication(owner, "constraints", "ACTIVE", "PRODUCTION");

        assertThatThrownBy(() -> insertApplication(UUID.randomUUID(), "missing-owner", "ACTIVE", "PRODUCTION"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertApplication(owner, "bad-status", "UNKNOWN", "PRODUCTION"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertApplication(owner, "bad-env", "ACTIVE", "STAGING"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", owner))
                .isInstanceOf(DataIntegrityViolationException.class);

        String hash = "a".repeat(64);
        insertApiKey(applicationId, UUID.randomUUID(), hash, "ACTIVE", null);
        assertThatThrownBy(() -> insertApiKey(applicationId, UUID.randomUUID(), hash, "ACTIVE", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertApiKey(UUID.randomUUID(), UUID.randomUUID(), "b".repeat(64), "ACTIVE", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertApiKey(applicationId, UUID.randomUUID(), "c".repeat(64), "REVOKED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertApiKey(applicationId, UUID.randomUUID(), "d".repeat(64), "UNKNOWN", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM applications WHERE id = ?", applicationId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void flywayAppliedThroughV8AndHibernateValidatedSchema() {
        assertThat(jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM applications", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM api_keys", Long.class)).isZero();
    }

    private UUID insertUser(String subject) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, google_subject, email, display_name, status,
                    last_login_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, subject, subject + "@example.com", subject);
        return id;
    }

    private UUID createApplication(
            OAuth2AuthenticationToken authentication,
            String name,
            String slug,
            String environment) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/applications")
                        .with(authentication(authentication)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationRequest(name, slug, environment)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private MvcResult createApiKey(
            OAuth2AuthenticationToken authentication,
            UUID applicationId,
            String name) throws Exception {
        return mockMvc.perform(post("/api/v1/applications/{id}/api-keys", applicationId)
                        .with(authentication(authentication)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("name", name))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keyHash").doesNotExist())
                .andReturn();
    }

    private String applicationRequest(String name, String slug, String environment) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "name", name,
                "slug", slug,
                "environment", environment));
    }

    private UUID responseId(MvcResult result) throws Exception {
        return UUID.fromString(response(result).get("id").asText());
    }

    private JsonNode response(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private OAuth2AuthenticationToken authenticationFor(UUID userId) {
        OidcUser delegate = mock(OidcUser.class);
        Collection<? extends GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        doReturn(authorities).when(delegate).getAuthorities();
        when(delegate.getName()).thenReturn(userId.toString());
        AuthenticatedOidcUser principal = new AuthenticatedOidcUser(
                delegate, userId, userId + "@example.com", "Developer", null);
        return new OAuth2AuthenticationToken(principal, authorities, "google");
    }

    private UUID insertApplication(UUID owner, String slug, String status, String environment) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO applications (
                    id, owner_user_id, name, slug, status, environment, created_at, updated_at
                ) VALUES (?, ?, 'Application', ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, owner, slug, status, environment);
        return id;
    }

    private void insertApiKey(UUID applicationId, UUID id, String hash, String status, Instant revokedAt) {
        jdbcTemplate.update("""
                INSERT INTO api_keys (
                    id, application_id, name, key_prefix, key_hash, status, created_at, revoked_at
                ) VALUES (?, ?, 'Key', 'whk_live_ab12', ?, ?, CURRENT_TIMESTAMP, ?)
                """, id, applicationId, hash, status, revokedAt);
    }
}
