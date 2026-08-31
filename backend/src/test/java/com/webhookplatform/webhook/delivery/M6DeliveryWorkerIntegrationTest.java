package com.webhookplatform.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import com.webhookplatform.webhook.signature.WebhookSigningSecretService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "GOOGLE_CLIENT_ID=test-client-id",
        "GOOGLE_CLIENT_SECRET=test-client-secret",
        "webhook-platform.worker.enabled=true",
        "webhook-platform.worker.batch-size=2",
        "webhook-platform.worker.poll-interval=PT1H",
        "webhook-platform.worker.request-timeout=PT1S"
})
@Testcontainers
class M6DeliveryWorkerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WebhookDeliveryClaimService claims;
    @Autowired private WebhookDeliveryWorker worker;
    @Autowired private OutboundWebhookClient outboundWebhookClient;
    @Autowired private WebhookDeliveryPayloadFactory payloadFactory;
    @Autowired private WebhookSigningSecretService signingSecrets;
    @SpyBean private DestinationAddressPolicy destinationAddressPolicy;
    private HttpServer server;

    @BeforeEach
    void cleanDatabase() {
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
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void claimsWithSkipLockedAndDoesNotAllowTwoWorkersToClaimTheSameRows() throws Exception {
        UUID application = insertApplication();
        UUID endpoint = insertEndpoint(application, "https://public.example.test/hook");
        for (int index = 0; index < 4; index++) insertDelivery(application, endpoint, "source-" + index, "https://public.example.test/hook");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> claims.claimPending(4));
            var second = executor.submit(() -> claims.claimPending(4));
            List<ClaimedDelivery> claimed = new java.util.ArrayList<>();
            claimed.addAll(first.get());
            claimed.addAll(second.get());
            assertThat(claimed).hasSize(4);
            assertThat(claimed.stream().map(ClaimedDelivery::deliveryId)).doesNotHaveDuplicates();
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_deliveries WHERE status = 'PROCESSING'", Long.class)).isEqualTo(4L);
    }

    @Test
    void workerHonorsTheConfiguredBatchSizeForOneTick() throws Exception {
        UUID application = insertApplication();
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/hook", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        UUID endpoint = insertEndpoint(application, "https://current.example.test/hook");
        String targetUrl = "http://localhost:" + server.getAddress().getPort() + "/hook";
        for (int index = 0; index < 5; index++) {
            insertDelivery(application, endpoint, "batch-" + index, targetUrl);
        }

        worker.processOnce();

        assertThat(requests).hasValue(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_deliveries WHERE status = 'DELIVERED'", Long.class)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_deliveries WHERE status = 'PENDING'", Long.class)).isEqualTo(3L);
    }

    @Test
    void finalizesAClaimedSnapshotDeliveryOutsideTheClaimTransaction() throws Exception {
        UUID application = insertApplication();
        String[] body = new String[1];
        String[] deliveryHeader = new String[1];
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/hook", exchange -> {
            body[0] = new String(exchange.getRequestBody().readAllBytes());
            deliveryHeader[0] = exchange.getRequestHeaders().getFirst("X-Webhook-Delivery-Id");
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.start();
        UUID endpoint = insertEndpoint(application, "https://changed.example.test/hook");
        UUID delivery = insertDelivery(application, endpoint, "snapshot", "http://localhost:" + server.getAddress().getPort() + "/hook");

        worker.processOnce();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id = ?", String.class, delivery)).isEqualTo("DELIVERED");
        assertThat(body[0]).contains("\"sourceEventId\":\"snapshot\"").contains("\"payload\":{\"status\":\"completed\"}");
        assertThat(deliveryHeader[0]).isEqualTo(delivery.toString());
    }

    @Test
    void anOldClaimTokenCannotFinalizeAnActivelyNewerClaim() {
        UUID application = insertApplication();
        UUID endpoint = insertEndpoint(application, "https://public.example.test/hook");
        UUID stale = insertDelivery(application, endpoint, "stale", "https://public.example.test/hook");
        ClaimedDelivery firstClaim = claims.claimPending(1).get(0);
        UUID staleToken = firstClaim.claimToken();
        jdbcTemplate.update("UPDATE webhook_deliveries SET processing_started_at=CURRENT_TIMESTAMP - INTERVAL '10 minutes' WHERE id=?", stale);

        assertThat(claims.recoverStaleProcessing()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, stale)).isEqualTo("PENDING");

        ClaimedDelivery secondClaim = claims.claimPending(1).get(0);
        assertThat(secondClaim.claimToken()).isNotEqualTo(staleToken);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, stale)).isEqualTo("PROCESSING");
        assertThat(jdbcTemplate.queryForObject("SELECT claim_token FROM webhook_deliveries WHERE id=?", UUID.class, stale)).isEqualTo(secondClaim.claimToken());

        assertThat(claims.finalizeClaim(stale, staleToken, WebhookDeliveryStatus.DELIVERED)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, stale)).isEqualTo("PROCESSING");
        assertThat(jdbcTemplate.queryForObject("SELECT claim_token FROM webhook_deliveries WHERE id=?", UUID.class, stale)).isEqualTo(secondClaim.claimToken());

        assertThat(claims.finalizeClaim(stale, secondClaim.claimToken(), WebhookDeliveryStatus.DELIVERED)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, stale)).isEqualTo("DELIVERED");
        assertThat(jdbcTemplate.queryForObject("SELECT claim_token FROM webhook_deliveries WHERE id=?", UUID.class, stale)).isNull();
    }

    @Test
    void processesSnapshottedDeliveriesAfterEndpointDisableAndSubscriptionDeletion() throws Exception {
        UUID application = insertApplication();
        AtomicInteger disabledEndpointRequests = new AtomicInteger();
        AtomicInteger deletedSubscriptionRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/disabled", exchange -> {
            disabledEndpointRequests.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/deleted", exchange -> {
            deletedSubscriptionRequests.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        UUID disabledEndpoint = insertEndpoint(application, "https://changed-disabled.example.test/hook");
        UUID deletedSubscriptionEndpoint = insertEndpoint(application, "https://changed-deleted.example.test/hook");
        UUID disabledSubscription = insertSubscription(disabledEndpoint);
        UUID deletedSubscription = insertSubscription(deletedSubscriptionEndpoint);
        UUID disabledDelivery = insertDelivery(application, disabledEndpoint, "disabled-after-fanout",
                "http://localhost:" + server.getAddress().getPort() + "/disabled");
        UUID deletedDelivery = insertDelivery(application, deletedSubscriptionEndpoint, "subscription-deleted-after-fanout",
                "http://localhost:" + server.getAddress().getPort() + "/deleted");

        jdbcTemplate.update("UPDATE webhook_endpoints SET status='DISABLED' WHERE id=?", disabledEndpoint);
        jdbcTemplate.update("DELETE FROM webhook_subscriptions WHERE id=?", deletedSubscription);

        worker.processOnce();

        assertThat(disabledEndpointRequests).hasValue(1);
        assertThat(deletedSubscriptionRequests).hasValue(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, disabledDelivery)).isEqualTo("DELIVERED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, deletedDelivery)).isEqualTo("DELIVERED");
        assertThat(jdbcTemplate.queryForObject("SELECT target_url FROM webhook_deliveries WHERE id=?", String.class, disabledDelivery))
                .endsWith("/disabled");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_subscriptions WHERE id=?", Long.class, disabledSubscription)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_subscriptions WHERE id=?", Long.class, deletedSubscription)).isZero();
    }

    @Test
    void classifiesHttpOutcomesWithoutFollowingRedirectsAndFailsConnectionAndTimeouts() throws Exception {
        UUID application = insertApplication();
        AtomicInteger response = new AtomicInteger(200);
        AtomicInteger redirects = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/hook", exchange -> {
            int code = response.get();
            if (code == 302) exchange.getResponseHeaders().add("Location", "/redirected");
            exchange.sendResponseHeaders(code, -1);
            exchange.close();
        });
        server.createContext("/redirected", exchange -> { redirects.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.createContext("/timeout", exchange -> { try { Thread.sleep(1500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.start();
        String base = "http://localhost:" + server.getAddress().getPort();
        UUID endpoint = insertEndpoint(application, "https://changed.example.test/hook");
        for (int code : new int[] {200, 201, 202, 204, 302, 404, 500}) {
            response.set(code);
            UUID delivery = insertDelivery(application, endpoint, "status-" + code, base + "/hook");
            worker.processOnce();
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, delivery))
                    .isEqualTo(code >= 200 && code < 300 ? "DELIVERED" : code == 500 ? "RETRY_SCHEDULED" : "FAILED");
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_delivery_attempts WHERE delivery_id=?", String.class, delivery))
                    .isEqualTo(code >= 200 && code < 300 ? "SUCCEEDED" : "FAILED");
            assertThat(jdbcTemplate.queryForObject("SELECT http_status_code FROM webhook_delivery_attempts WHERE delivery_id=?", Integer.class, delivery))
                    .isEqualTo(code);
            assertThat(jdbcTemplate.queryForObject("SELECT error_code FROM webhook_delivery_attempts WHERE delivery_id=?", String.class, delivery))
                    .isEqualTo(code >= 200 && code < 300 ? null : "HTTP_ERROR");
        }
        assertThat(redirects).hasValue(0);
        UUID connectionFailure = insertDelivery(application, endpoint, "connection", "http://127.0.0.1:1/hook");
        worker.processOnce();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, connectionFailure)).isEqualTo("RETRY_SCHEDULED");
        assertThat(jdbcTemplate.queryForObject("SELECT error_code FROM webhook_delivery_attempts WHERE delivery_id=?", String.class, connectionFailure)).isEqualTo("CONNECTION_ERROR");
        UUID timeout = insertDelivery(application, endpoint, "timeout", base + "/timeout");
        worker.processOnce();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, timeout)).isEqualTo("RETRY_SCHEDULED");
        assertThat(jdbcTemplate.queryForObject("SELECT error_code FROM webhook_delivery_attempts WHERE delivery_id=?", String.class, timeout)).isEqualTo("TIMEOUT");
    }

    @Test
    void failsDeliveryWhenTheActualClientEncountersAnUntrustedTlsCertificate() throws Exception {
        UUID application = insertApplication();
        Path keyStore = Files.createTempFile("m6-untrusted", ".jks");
        try {
            Files.delete(keyStore);
            Process keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(), "-genkeypair", "-alias", "test", "-keyalg", "RSA", "-storetype", "JKS", "-keystore", keyStore.toString(), "-storepass", "changeit", "-keypass", "changeit", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-validity", "1", "-noprompt")
                    .redirectErrorStream(true).start();
            String keytoolOutput = new String(keytool.getInputStream().readAllBytes());
            assertThat(keytool.waitFor()).withFailMessage(keytoolOutput).isZero();
            KeyStore store = KeyStore.getInstance("JKS");
            try (var input = Files.newInputStream(keyStore)) { store.load(input, "changeit".toCharArray()); }
            KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            managers.init(store, "changeit".toCharArray());
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(managers.getKeyManagers(), null, null);
            HttpsServer https = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            https.setHttpsConfigurator(new HttpsConfigurator(ssl));
            https.createContext("/hook", exchange -> { exchange.sendResponseHeaders(200, -1); exchange.close(); });
            https.start();
            try {
                doNothing().when(destinationAddressPolicy).validate(anyString(), any(java.net.InetAddress[].class), anyBoolean());
                UUID endpoint = insertEndpoint(application, "https://changed.example.test/hook");
                UUID delivery = insertDelivery(application, endpoint, "tls", "https://127.0.0.1:" + https.getAddress().getPort() + "/hook");
                ClaimedDelivery claimed = claims.claimPending(1).get(0);
                assertThatThrownBy(() -> outboundWebhookClient.post(claimed, payloadFactory.create(claimed)))
                        .isInstanceOf(SSLHandshakeException.class)
                        .hasMessageContaining("PKIX path building failed");
                jdbcTemplate.update("UPDATE webhook_deliveries SET status='PENDING', processing_started_at=NULL, claim_token=NULL WHERE id=?", delivery);
                worker.processOnce();
                assertThat(jdbcTemplate.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, delivery)).isEqualTo("FAILED");
                assertThat(jdbcTemplate.queryForObject("SELECT error_code FROM webhook_delivery_attempts WHERE delivery_id=?", String.class, delivery)).isEqualTo("TLS_ERROR");
            } finally { https.stop(0); }
        } finally { Files.deleteIfExists(keyStore); }
    }

    private UUID insertApplication() {
        UUID user = UUID.randomUUID();
        UUID application = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, google_subject, email, display_name, status, last_login_at, created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", user, user.toString(), user + "@example.test", "Worker");
        jdbcTemplate.update("INSERT INTO applications (id, owner_user_id, name, slug, status, environment, created_at, updated_at) VALUES (?, ?, 'App', ?, 'ACTIVE', 'DEVELOPMENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", application, user, "worker-" + application);
        return application;
    }

    private UUID insertEndpoint(UUID application, String url) {
        UUID endpoint = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO webhook_endpoints (id, application_id, name, url, status, created_at, updated_at) VALUES (?, ?, 'Endpoint', ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", endpoint, application, url);
        signingSecrets.provision(endpoint);
        return endpoint;
    }

    private UUID insertSubscription(UUID endpoint) {
        UUID subscription = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO webhook_subscriptions (id, endpoint_id, event_type, created_at) VALUES (?, ?, 'ai.solution.completed', CURRENT_TIMESTAMP)", subscription, endpoint);
        return subscription;
    }

    private UUID insertDelivery(UUID application, UUID endpoint, String sourceId, String targetUrl) {
        UUID event = UUID.randomUUID();
        UUID delivery = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO webhook_events (id, application_id, source_event_id, event_type, payload, created_at) VALUES (?, ?, ?, 'ai.solution.completed', '{\"status\":\"completed\"}'::jsonb, CURRENT_TIMESTAMP)", event, application, sourceId);
        jdbcTemplate.update("INSERT INTO webhook_deliveries (id, event_id, endpoint_id, target_url, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", delivery, event, endpoint, targetUrl);
        return delivery;
    }
}
