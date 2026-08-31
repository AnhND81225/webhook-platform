package com.webhookplatform.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import com.webhookplatform.webhook.signature.WebhookSigningSecretService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {
        "GOOGLE_CLIENT_ID=test-client-id", "GOOGLE_CLIENT_SECRET=test-client-secret",
        "webhook-platform.worker.enabled=true", "webhook-platform.worker.batch-size=10",
        "webhook-platform.worker.poll-interval=PT1H", "webhook-platform.worker.request-timeout=PT1S",
        "webhook-platform.retry.enabled=true", "webhook-platform.retry.max-attempts=5",
        "webhook-platform.retry.delays=PT10S,PT30S,PT2M,PT10M"
})
@Import(M8RetryIntegrationTest.MutableClockConfiguration.class)
@Testcontainers
class M8RetryIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WebhookDeliveryWorker worker;
    @Autowired private WebhookDeliveryClaimService claims;
    @Autowired private WebhookDeliveryAttemptService attempts;
    @Autowired private MutableClock clock;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private WebhookSigningSecretService signingSecrets;
    @SpyBean private OutboundWebhookClient outboundWebhookClient;
    private HttpServer server;

    @BeforeEach
    void cleanDatabase() {
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
        jdbcTemplate.update("DELETE FROM webhook_delivery_attempts");
        jdbcTemplate.update("DELETE FROM webhook_signing_secrets");
        jdbcTemplate.update("DELETE FROM webhook_deliveries");
        jdbcTemplate.update("DELETE FROM webhook_events");
        jdbcTemplate.update("DELETE FROM webhook_subscriptions");
        jdbcTemplate.update("DELETE FROM webhook_endpoints");
        jdbcTemplate.update("DELETE FROM api_keys");
        jdbcTemplate.update("DELETE FROM applications");
        jdbcTemplate.update("DELETE FROM users");
    }

    @AfterEach
    void stopServer() { if (server != null) server.stop(0); }

    @Test
    void schedulesRetryAtExactDelayThenDeliversOnTheNextDueAttempt() throws Exception {
        AtomicInteger response = new AtomicInteger(500);
        AtomicInteger requests = new AtomicInteger();
        String target = startServer(response, requests);
        UUID application = insertApplication();
        UUID endpoint = insertEndpoint(application);
        UUID delivery = insertDelivery(application, endpoint, "retry-success", target);

        worker.processOnce();

        assertDelivery(delivery, "RETRY_SCHEDULED", clock.instant().plusSeconds(10));
        assertAttempt(delivery, 1, "FAILED", 500, "HTTP_ERROR");
        worker.processOnce();
        assertThat(requests).hasValue(1);
        clock.advanceSeconds(10);
        response.set(200);
        worker.processOnce();
        assertDelivery(delivery, "DELIVERED", null);
        assertAttempt(delivery, 2, "SUCCEEDED", 200, null);
        assertThat(requests).hasValue(2);
    }

    @Test
    void appliesPlatformBackoffAndCapsRetryableFailuresAtFiveAttempts() throws Exception {
        AtomicInteger response = new AtomicInteger(500);
        String target = startServer(response, new AtomicInteger());
        UUID application = insertApplication();
        UUID endpoint = insertEndpoint(application);
        UUID delivery = insertDelivery(application, endpoint, "max-attempts", target);
        long[] delays = {10, 30, 120, 600};
        for (int attempt = 1; attempt <= 5; attempt++) {
            worker.processOnce();
            if (attempt < 5) {
                assertDelivery(delivery, "RETRY_SCHEDULED", clock.instant().plusSeconds(delays[attempt - 1]));
                clock.advanceSeconds(delays[attempt - 1]);
            }
        }
        assertDelivery(delivery, "FAILED", null);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_delivery_attempts WHERE delivery_id=?", Long.class, delivery)).isEqualTo(5L);
        worker.processOnce();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_delivery_attempts WHERE delivery_id=?", Long.class, delivery)).isEqualTo(5L);
    }

    @Test
    void claimsOnlyDueRetriesAndUsesSkipLockedStateTransition() {
        UUID application = insertApplication();
        UUID endpoint = insertEndpoint(application);
        UUID future = insertDelivery(application, endpoint, "future", "https://public.example.test/future");
        UUID due = insertDelivery(application, endpoint, "due", "https://public.example.test/due");
        jdbcTemplate.update("UPDATE webhook_deliveries SET status='RETRY_SCHEDULED', next_retry_at=? WHERE id=?", timestamp(clock.instant().plusSeconds(1)), future);
        jdbcTemplate.update("UPDATE webhook_deliveries SET status='RETRY_SCHEDULED', next_retry_at=? WHERE id=?", timestamp(clock.instant()), due);

        var claimed = claims.claimPending(10);

        assertThat(claimed).extracting(ClaimedDelivery::deliveryId).containsExactly(due);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, due)).isEqualTo("PROCESSING");
        assertThat(jdbcTemplate.queryForObject("SELECT next_retry_at FROM webhook_deliveries WHERE id=?", Instant.class, due)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, future)).isEqualTo("RETRY_SCHEDULED");
    }

    @Test
    void concurrentClaimersUsePostgresSkipLockedForDueRetriesWithoutDuplicateClaims() throws Exception {
        UUID application = insertApplication();
        UUID endpoint = insertEndpoint(application);
        UUID first = insertDelivery(application, endpoint, "concurrent-one", "https://public.example.test/one");
        UUID second = insertDelivery(application, endpoint, "concurrent-two", "https://public.example.test/two");
        for (UUID delivery : List.of(first, second)) {
            jdbcTemplate.update("UPDATE webhook_deliveries SET status='RETRY_SCHEDULED', next_retry_at=? WHERE id=?", timestamp(clock.instant()), delivery);
        }
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> { ready.countDown(); start.await(); return claims.claimPending(1); });
            var two = executor.submit(() -> { ready.countDown(); start.await(); return claims.claimPending(1); });
            ready.await();
            start.countDown();
            List<ClaimedDelivery> claimed = new ArrayList<>();
            claimed.addAll(one.get());
            claimed.addAll(two.get());
            assertThat(claimed).hasSize(2);
            assertThat(claimed).extracting(ClaimedDelivery::deliveryId).containsExactlyInAnyOrder(first, second).doesNotHaveDuplicates();
        } finally {
            executor.shutdownNow();
        }
        for (UUID delivery : List.of(first, second)) {
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, delivery)).isEqualTo("PROCESSING");
            assertThat(jdbcTemplate.queryForObject("SELECT claim_token IS NOT NULL FROM webhook_deliveries WHERE id=?", Boolean.class, delivery)).isTrue();
            assertThat(jdbcTemplate.queryForObject("SELECT next_retry_at FROM webhook_deliveries WHERE id=?", Instant.class, delivery)).isNull();
        }
    }

    @Test
    void workerSchedulesHttp408WithM7MetadataAndNoEarlySecondRequest() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        String target = startServer(new AtomicInteger(408), requests);
        UUID application = insertApplication();
        UUID delivery = insertDelivery(application, insertEndpoint(application), "http-408", target);

        worker.processOnce();

        assertAttempt(delivery, 1, "FAILED", 408, "HTTP_ERROR");
        assertDelivery(delivery, "RETRY_SCHEDULED", clock.instant().plusSeconds(10));
        assertThat(requests).hasValue(1);
        worker.processOnce();
        assertThat(requests).hasValue(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_delivery_attempts WHERE delivery_id=?", Long.class, delivery)).isEqualTo(1L);
    }

    @Test
    void workerSchedulesDeterministicDnsFailureWithoutExternalResolution() throws Exception {
        doThrow(new java.net.UnknownHostException("test-only-dns-failure"))
                .when(outboundWebhookClient).post(any(ClaimedDelivery.class), any(byte[].class));
        UUID application = insertApplication();
        UUID delivery = insertDelivery(application, insertEndpoint(application), "dns", "https://public.example.test/dns");

        worker.processOnce();

        assertAttempt(delivery, 1, "FAILED", null, "DNS_ERROR");
        assertDelivery(delivery, "RETRY_SCHEDULED", clock.instant().plusSeconds(10));
    }

    @Test
    void recoversAbandonedAttemptAsScheduledRetryButCrashBeforeAttemptReturnsPending() {
        UUID application = insertApplication();
        UUID endpoint = insertEndpoint(application);
        UUID abandoned = insertDelivery(application, endpoint, "abandoned", "https://public.example.test/a");
        ClaimedDelivery abandonedClaim = claims.claimPending(1).get(0);
        StartedWebhookDeliveryAttempt started = attempts.startAttempt(abandonedClaim).orElseThrow();
        jdbcTemplate.update("UPDATE webhook_deliveries SET processing_started_at=? WHERE id=?", timestamp(clock.instant().minusSeconds(301)), abandoned);
        assertThat(claims.recoverStaleProcessing()).isEqualTo(1);
        assertAttempt(abandoned, 1, "ABANDONED", null, null);
        assertDelivery(abandoned, "RETRY_SCHEDULED", clock.instant().plusSeconds(10));

        UUID beforeAttempt = insertDelivery(application, endpoint, "before-attempt", "https://public.example.test/b");
        ClaimedDelivery bareClaim = claims.claimPending(1).stream().filter(claim -> claim.deliveryId().equals(beforeAttempt)).findFirst().orElseThrow();
        assertThat(bareClaim).isNotNull();
        jdbcTemplate.update("UPDATE webhook_deliveries SET processing_started_at=? WHERE id=?", timestamp(clock.instant().minusSeconds(301)), beforeAttempt);
        assertThat(claims.recoverStaleProcessing()).isEqualTo(1);
        assertDelivery(beforeAttempt, "PENDING", null);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_delivery_attempts WHERE delivery_id=?", Long.class, beforeAttempt)).isZero();
        assertThat(started.attemptNumber()).isEqualTo(1);
    }

    @Test
    void abandonsFifthAttemptAsTerminalFailureWithoutMakingItClaimableAgain() {
        UUID application = insertApplication();
        UUID endpoint = insertEndpoint(application);
        UUID delivery = insertDelivery(application, endpoint, "abandoned-five", "https://public.example.test/five");
        for (int number = 1; number <= 4; number++) {
            jdbcTemplate.update("""
                    INSERT INTO webhook_delivery_attempts
                    (id, delivery_id, attempt_number, claim_token, status, started_at, completed_at, duration_ms, http_status_code, error_code)
                    VALUES (?, ?, ?, ?, 'FAILED', ?, ?, 1, 500, 'HTTP_ERROR')
                    """, UUID.randomUUID(), delivery, number, UUID.randomUUID(), timestamp(clock.instant()), timestamp(clock.instant()));
        }
        ClaimedDelivery claim = claims.claimPending(1).get(0);
        StartedWebhookDeliveryAttempt fifth = attempts.startAttempt(claim).orElseThrow();
        assertThat(fifth.attemptNumber()).isEqualTo(5);
        jdbcTemplate.update("UPDATE webhook_deliveries SET processing_started_at=? WHERE id=?", timestamp(clock.instant().minusSeconds(301)), delivery);

        assertThat(claims.recoverStaleProcessing()).isEqualTo(1);

        assertAttempt(delivery, 5, "ABANDONED", null, null);
        assertDelivery(delivery, "FAILED", null);
        assertThat(jdbcTemplate.queryForObject("SELECT processing_started_at FROM webhook_deliveries WHERE id=?", Instant.class, delivery)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT claim_token FROM webhook_deliveries WHERE id=?", UUID.class, delivery)).isNull();
        clock.advanceSeconds(3600);
        assertThat(claims.claimPending(10)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_delivery_attempts WHERE delivery_id=?", Long.class, delivery)).isEqualTo(5L);
    }

    @Test
    void terminalOutcomesDoNotScheduleRetryAndRetryCompletionRollsBackAtomically() {
        UUID application = insertApplication();
        UUID endpoint = insertEndpoint(application);
        UUID terminal = insertDelivery(application, endpoint, "terminal", "https://public.example.test/t");
        ClaimedDelivery terminalClaim = claims.claimPending(1).get(0);
        StartedWebhookDeliveryAttempt terminalAttempt = attempts.startAttempt(terminalClaim).orElseThrow();
        assertThat(attempts.completeAttemptAndResolve(terminalAttempt, false, 404, WebhookDeliveryAttemptErrorCode.HTTP_ERROR, 1)).isTrue();
        assertDelivery(terminal, "FAILED", null);

        UUID atomic = insertDelivery(application, endpoint, "atomic", "https://public.example.test/r");
        ClaimedDelivery claim = claims.claimPending(1).stream().filter(value -> value.deliveryId().equals(atomic)).findFirst().orElseThrow();
        StartedWebhookDeliveryAttempt attempt = attempts.startAttempt(claim).orElseThrow();
        jdbcTemplate.execute("""
                CREATE FUNCTION m8_force_retry_failure() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'forced M8 retry completion failure'; END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER m8_force_retry_failure BEFORE UPDATE OF status ON webhook_deliveries
                FOR EACH ROW WHEN (OLD.status = 'PROCESSING' AND NEW.status = 'RETRY_SCHEDULED')
                EXECUTE FUNCTION m8_force_retry_failure()
                """);
        try {
            assertThatThrownBy(() -> attempts.completeAttemptAndResolve(attempt, false, 503, WebhookDeliveryAttemptErrorCode.HTTP_ERROR, 1))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS m8_force_retry_failure ON webhook_deliveries");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS m8_force_retry_failure()");
        }
        entityManager.clear();
        transactionTemplate.executeWithoutResult(ignored -> {
            assertAttempt(atomic, 1, "IN_PROGRESS", null, null);
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, atomic)).isEqualTo("PROCESSING");
            assertThat(jdbcTemplate.queryForObject("SELECT claim_token FROM webhook_deliveries WHERE id=?", UUID.class, atomic)).isEqualTo(claim.claimToken());
            assertThat(jdbcTemplate.queryForObject("SELECT next_retry_at FROM webhook_deliveries WHERE id=?", Instant.class, atomic)).isNull();
        });
    }

    @Test
    void flywayV8EnforcesRetryStateAndCreatesDueIndex() {
        UUID application = insertApplication();
        UUID endpoint = insertEndpoint(application);
        UUID delivery = insertDelivery(application, endpoint, "constraints", "https://public.example.test/c");
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE webhook_deliveries SET status='RETRY_SCHEDULED' WHERE id=?", delivery)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE webhook_deliveries SET next_retry_at=? WHERE id=?", timestamp(clock.instant()), delivery)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE indexname='idx_webhook_deliveries_retry_scheduled_due'", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");
    }

    private String startServer(AtomicInteger response, AtomicInteger requests) throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/hook", exchange -> { requests.incrementAndGet(); exchange.sendResponseHeaders(response.get(), -1); exchange.close(); });
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/hook";
    }

    private void assertDelivery(UUID delivery, String status, Instant nextRetryAt) {
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, delivery)).isEqualTo(status);
        assertThat(jdbcTemplate.queryForObject("SELECT next_retry_at FROM webhook_deliveries WHERE id=?", Instant.class, delivery)).isEqualTo(nextRetryAt);
    }

    private void assertAttempt(UUID delivery, int number, String status, Integer http, String error) {
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_delivery_attempts WHERE delivery_id=? AND attempt_number=?", String.class, delivery, number)).isEqualTo(status);
        assertThat(jdbcTemplate.queryForObject("SELECT http_status_code FROM webhook_delivery_attempts WHERE delivery_id=? AND attempt_number=?", Integer.class, delivery, number)).isEqualTo(http);
        assertThat(jdbcTemplate.queryForObject("SELECT error_code FROM webhook_delivery_attempts WHERE delivery_id=? AND attempt_number=?", String.class, delivery, number)).isEqualTo(error);
    }

    private OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private UUID insertApplication() {
        UUID user = UUID.randomUUID(); UUID application = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, google_subject, email, display_name, status, last_login_at, created_at, updated_at) VALUES (?, ?, ?, 'M8', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", user, user.toString(), user + "@example.test");
        jdbcTemplate.update("INSERT INTO applications (id, owner_user_id, name, slug, status, environment, created_at, updated_at) VALUES (?, ?, 'M8', ?, 'ACTIVE', 'DEVELOPMENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", application, user, "m8-" + application);
        return application;
    }

    private UUID insertEndpoint(UUID application) {
        UUID endpoint = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO webhook_endpoints (id, application_id, name, url, status, created_at, updated_at) VALUES (?, ?, 'M8', 'https://public.example.test/hook', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", endpoint, application);
        signingSecrets.provision(endpoint);
        return endpoint;
    }

    private UUID insertDelivery(UUID application, UUID endpoint, String sourceId, String target) {
        UUID event = UUID.randomUUID(); UUID delivery = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO webhook_events (id, application_id, source_event_id, event_type, payload, created_at) VALUES (?, ?, ?, 'ai.solution.completed', '{\"status\":\"completed\"}'::jsonb, CURRENT_TIMESTAMP)", event, application, sourceId);
        jdbcTemplate.update("INSERT INTO webhook_deliveries (id, event_id, endpoint_id, target_url, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", delivery, event, endpoint, target);
        return delivery;
    }

    @TestConfiguration
    static class MutableClockConfiguration {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(Instant.parse("2030-01-01T00:00:00Z")); }
    }

    static final class MutableClock extends Clock {
        private volatile Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        void set(Instant instant) { this.instant = instant; }
        void advanceSeconds(long seconds) { instant = instant.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
