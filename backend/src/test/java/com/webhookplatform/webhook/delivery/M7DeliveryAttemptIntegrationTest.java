package com.webhookplatform.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "GOOGLE_CLIENT_ID=test-client-id",
        "GOOGLE_CLIENT_SECRET=test-client-secret",
        "webhook-platform.worker.enabled=true",
        "webhook-platform.worker.batch-size=10",
        "webhook-platform.worker.poll-interval=PT1H",
        "webhook-platform.worker.request-timeout=PT1S"
})
@Testcontainers
class M7DeliveryAttemptIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WebhookDeliveryWorker worker;
    @Autowired private WebhookDeliveryClaimService claims;
    @Autowired private WebhookDeliveryAttemptService attempts;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;
    @SpyBean private DestinationAddressPolicy destinationAddressPolicy;
    private HttpServer server;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM webhook_delivery_attempts");
        jdbcTemplate.update("DELETE FROM webhook_deliveries");
        jdbcTemplate.update("DELETE FROM webhook_events");
        jdbcTemplate.update("DELETE FROM webhook_subscriptions");
        jdbcTemplate.update("DELETE FROM webhook_endpoints");
        jdbcTemplate.update("DELETE FROM api_keys");
        jdbcTemplate.update("DELETE FROM applications");
        jdbcTemplate.update("DELETE FROM users");
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void recordsSuccessAndHttpFailureWithoutPersistingResponseContent() throws Exception {
        UUID application = insertApplication();
        AtomicInteger responseCode = new AtomicInteger(204);
        AtomicInteger requestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/hook", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(responseCode.get(), -1);
            exchange.close();
        });
        server.start();
        UUID endpoint = insertEndpoint(application);
        String target = "http://localhost:" + server.getAddress().getPort() + "/hook";
        UUID successfulDelivery = insertDelivery(application, endpoint, "success", target);

        worker.processOnce();

        assertAttempt(successfulDelivery, "SUCCEEDED", 204, null);
        assertThat(requestCount).hasValue(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_delivery_attempts WHERE delivery_id=?", Long.class, successfulDelivery)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT started_at IS NOT NULL FROM webhook_delivery_attempts WHERE delivery_id=?", Boolean.class, successfulDelivery)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT completed_at IS NOT NULL FROM webhook_delivery_attempts WHERE delivery_id=?", Boolean.class, successfulDelivery)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT completed_at >= started_at FROM webhook_delivery_attempts WHERE delivery_id=?", Boolean.class, successfulDelivery)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT duration_ms FROM webhook_delivery_attempts WHERE delivery_id=?", Long.class, successfulDelivery)).isGreaterThanOrEqualTo(0L);
        responseCode.set(503);
        UUID failedDelivery = insertDelivery(application, endpoint, "http-failure", target);
        worker.processOnce();

        assertAttempt(failedDelivery, "FAILED", 503, "HTTP_ERROR");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, failedDelivery)).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_name='webhook_delivery_attempts' AND column_name IN ('response_body', 'payload', 'target_url')", Long.class)).isZero();
    }

    @Test
    void records429AsOneFailedHttpAttemptWithoutAnAutomaticRetry() throws Exception {
        UUID application = insertApplication();
        AtomicInteger requestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/rate-limited", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        server.start();
        UUID endpoint = insertEndpoint(application);
        UUID delivery = insertDelivery(application, endpoint, "rate-limited", "http://localhost:" + server.getAddress().getPort() + "/rate-limited");

        worker.processOnce();

        assertThat(requestCount).hasValue(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, delivery)).isEqualTo("FAILED");
        assertAttempt(delivery, "FAILED", 429, "HTTP_ERROR");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_delivery_attempts WHERE delivery_id=?", Long.class, delivery)).isEqualTo(1L);
    }

    @Test
    void recordsSsrfRejectionWithoutSendingTheRequest() throws Exception {
        UUID application = insertApplication();
        AtomicInteger requestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/blocked", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        doThrow(new UnsafeWebhookDestinationException()).when(destinationAddressPolicy)
                .validate(anyString(), any(java.net.InetAddress[].class), anyBoolean());
        UUID endpoint = insertEndpoint(application);
        UUID delivery = insertDelivery(application, endpoint, "ssrf", "http://localhost:" + server.getAddress().getPort() + "/blocked");

        worker.processOnce();

        assertThat(requestCount).hasValue(0);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, delivery)).isEqualTo("FAILED");
        assertAttempt(delivery, "FAILED", null, "SSRF_REJECTED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_delivery_attempts WHERE delivery_id=?", Long.class, delivery)).isEqualTo(1L);
    }

    @Test
    void recoversClaimBoundInProgressAttemptAsAbandonedAndDoesNotLetOldClaimComplete() {
        UUID application = insertApplication();
        UUID endpoint = insertEndpoint(application);
        UUID delivery = insertDelivery(application, endpoint, "stale", "https://public.example.test/hook");
        ClaimedDelivery firstClaim = claims.claimPending(1).get(0);
        StartedWebhookDeliveryAttempt firstAttempt = attempts.startAttempt(firstClaim).orElseThrow();
        jdbcTemplate.update("UPDATE webhook_deliveries SET processing_started_at=CURRENT_TIMESTAMP - INTERVAL '10 minutes' WHERE id=?", delivery);

        assertThat(claims.recoverStaleProcessing()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_delivery_attempts WHERE id=?", String.class, firstAttempt.attemptId())).isEqualTo("ABANDONED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, delivery)).isEqualTo("PENDING");

        ClaimedDelivery secondClaim = claims.claimPending(1).get(0);
        StartedWebhookDeliveryAttempt secondAttempt = attempts.startAttempt(secondClaim).orElseThrow();
        assertThat(secondAttempt.attemptNumber()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("SELECT attempt_number FROM webhook_delivery_attempts WHERE delivery_id=? ORDER BY attempt_number", Integer.class, delivery))
                .containsExactly(1, 2);
        assertThat(jdbcTemplate.queryForList("SELECT status FROM webhook_delivery_attempts WHERE delivery_id=? ORDER BY attempt_number", String.class, delivery))
                .containsExactly("ABANDONED", "IN_PROGRESS");
        assertThat(attempts.completeAttemptAndFinalize(firstAttempt, WebhookDeliveryStatus.DELIVERED, 204, null, 1)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, delivery)).isEqualTo("PROCESSING");
        assertThat(attempts.completeAttemptAndFinalize(secondAttempt, WebhookDeliveryStatus.DELIVERED, 204, null, 1)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, delivery)).isEqualTo("DELIVERED");
    }

    @Test
    void rollsBackAttemptCompletionWhenDeliveryFinalizationFails() {
        UUID application = insertApplication();
        UUID endpoint = insertEndpoint(application);
        UUID delivery = insertDelivery(application, endpoint, "atomic", "https://public.example.test/hook");
        ClaimedDelivery claim = claims.claimPending(1).get(0);
        StartedWebhookDeliveryAttempt attempt = attempts.startAttempt(claim).orElseThrow();
        jdbcTemplate.execute("""
                CREATE FUNCTION m7_force_completion_failure() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'forced M7 completion failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER m7_force_completion_failure
                BEFORE UPDATE OF status ON webhook_deliveries
                FOR EACH ROW WHEN (OLD.status = 'PROCESSING' AND NEW.status IN ('DELIVERED', 'FAILED'))
                EXECUTE FUNCTION m7_force_completion_failure()
                """);
        try {
            assertThatThrownBy(() -> attempts.completeAttemptAndFinalize(attempt, WebhookDeliveryStatus.DELIVERED, 204, null, 1))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS m7_force_completion_failure ON webhook_deliveries");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS m7_force_completion_failure()");
        }

        entityManager.clear();
        transactionTemplate.executeWithoutResult(ignored -> {
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_delivery_attempts WHERE id=?", String.class, attempt.attemptId()))
                    .isEqualTo("IN_PROGRESS");
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, delivery))
                    .isEqualTo("PROCESSING");
            assertThat(jdbcTemplate.queryForObject("SELECT claim_token FROM webhook_deliveries WHERE id=?", UUID.class, delivery))
                    .isEqualTo(claim.claimToken());
        });
    }

    @Test
    void flywayV7EnforcesAttemptConstraintsAndHistoryIndexes() {
        UUID application = insertApplication();
        UUID endpoint = insertEndpoint(application);
        UUID delivery = insertDelivery(application, endpoint, "constraints", "https://public.example.test/hook");
        UUID claimToken = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO webhook_delivery_attempts (id, delivery_id, attempt_number, claim_token, status, started_at) VALUES (?, ?, 1, ?, 'IN_PROGRESS', CURRENT_TIMESTAMP)", UUID.randomUUID(), delivery, claimToken);

        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO webhook_delivery_attempts (id, delivery_id, attempt_number, claim_token, status, started_at) VALUES (?, ?, 1, ?, 'IN_PROGRESS', CURRENT_TIMESTAMP)", UUID.randomUUID(), delivery, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO webhook_delivery_attempts (id, delivery_id, attempt_number, claim_token, status, started_at) VALUES (?, ?, 2, ?, 'UNKNOWN', CURRENT_TIMESTAMP)", UUID.randomUUID(), delivery, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class))
                .containsExactly("1", "2", "3", "4", "5", "6", "7");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE indexname='idx_webhook_delivery_attempts_delivery_history'", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE indexname='idx_webhook_delivery_attempts_in_progress_claim'", Long.class)).isEqualTo(1L);
    }

    private void assertAttempt(UUID delivery, String status, Integer httpStatus, String error) {
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_delivery_attempts WHERE delivery_id=?", String.class, delivery)).isEqualTo(status);
        assertThat(jdbcTemplate.queryForObject("SELECT http_status_code FROM webhook_delivery_attempts WHERE delivery_id=?", Integer.class, delivery)).isEqualTo(httpStatus);
        assertThat(jdbcTemplate.queryForObject("SELECT error_code FROM webhook_delivery_attempts WHERE delivery_id=?", String.class, delivery)).isEqualTo(error);
    }

    private UUID insertApplication() {
        UUID user = UUID.randomUUID();
        UUID application = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, google_subject, email, display_name, status, last_login_at, created_at, updated_at) VALUES (?, ?, ?, 'M7', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", user, user.toString(), user + "@example.test");
        jdbcTemplate.update("INSERT INTO applications (id, owner_user_id, name, slug, status, environment, created_at, updated_at) VALUES (?, ?, 'M7', ?, 'ACTIVE', 'DEVELOPMENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", application, user, "m7-" + application);
        return application;
    }

    private UUID insertEndpoint(UUID application) {
        UUID endpoint = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO webhook_endpoints (id, application_id, name, url, status, created_at, updated_at) VALUES (?, ?, 'M7', 'https://public.example.test/hook', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", endpoint, application);
        return endpoint;
    }

    private UUID insertDelivery(UUID application, UUID endpoint, String sourceEventId, String targetUrl) {
        UUID event = UUID.randomUUID();
        UUID delivery = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO webhook_events (id, application_id, source_event_id, event_type, payload, created_at) VALUES (?, ?, ?, 'ai.solution.completed', '{\"status\":\"completed\"}'::jsonb, CURRENT_TIMESTAMP)", event, application, sourceEventId);
        jdbcTemplate.update("INSERT INTO webhook_deliveries (id, event_id, endpoint_id, target_url, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", delivery, event, endpoint, targetUrl);
        return delivery;
    }
}
