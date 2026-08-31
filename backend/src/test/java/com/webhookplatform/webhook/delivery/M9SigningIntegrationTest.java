package com.webhookplatform.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.webhookplatform.webhook.signature.ProvisionedSigningSecret;
import com.webhookplatform.webhook.signature.WebhookSigningSecretService;

@SpringBootTest(properties = {"GOOGLE_CLIENT_ID=test-client-id", "GOOGLE_CLIENT_SECRET=test-client-secret",
        "webhook-platform.worker.enabled=true", "webhook-platform.worker.poll-interval=PT1H",
        "webhook-platform.retry.enabled=true", "webhook-platform.retry.delays=PT10S,PT30S,PT2M,PT10M"})
@Import(M9SigningIntegrationTest.MutableClockConfiguration.class)
@Testcontainers
class M9SigningIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired JdbcTemplate jdbc;
    @Autowired WebhookDeliveryWorker worker;
    @Autowired WebhookSigningSecretService secrets;
    @Autowired MutableClock clock;
    private HttpServer server;

    @BeforeEach void clean() {
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
        for (String table : List.of("webhook_delivery_attempts", "webhook_signing_secrets", "webhook_deliveries", "webhook_events", "webhook_subscriptions", "webhook_endpoints", "api_keys", "applications", "users")) jdbc.update("DELETE FROM " + table);
    }
    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void signsTheExactReceivedBytesAndRequiredHeaders() throws Exception {
        List<Captured> captured = new ArrayList<>();
        String url = server(captured, new AtomicInteger(200));
        UUID app = application(); UUID endpoint = endpoint(app); String secret = secrets.provision(endpoint).value(); UUID delivery = delivery(app, endpoint, url, "one");
        worker.processOnce();
        Captured request = captured.get(0);
        assertThat(request.header("Content-Type")).contains("application/json");
        assertThat(request.header("User-Agent")).isEqualTo("webhook-platform/1.0");
        assertThat(request.header("X-Webhook-Id")).isNotBlank();
        assertThat(request.header("X-Webhook-Delivery-Id")).isEqualTo(delivery.toString());
        assertThat(request.header("X-Webhook-Event")).isEqualTo("ai.solution.completed");
        assertThat(request.header("X-Webhook-Signature")).startsWith("v1=");
        assertThat(verify(secret, request.timestamp(), request.body(), request.header("X-Webhook-Signature"))).isTrue();
        byte[] changed = request.body().clone(); changed[0] ^= 1;
        assertThat(verify(secret, request.timestamp(), changed, request.header("X-Webhook-Signature"))).isFalse();
        assertThat(verify(secret, Long.toString(Long.parseLong(request.timestamp()) + 1), request.body(), request.header("X-Webhook-Signature"))).isFalse();
    }

    @Test void signingFailuresNeverSendUnsignedRequests() throws Exception {
        List<Captured> captured = new ArrayList<>(); String url = server(captured, new AtomicInteger(200));
        UUID app = application(); UUID endpoint = endpoint(app); UUID missing = delivery(app, endpoint, url, "missing");
        worker.processOnce();
        assertTerminalSigningFailure(missing); assertThat(captured).isEmpty();
        String raw = secrets.provision(endpoint).value();
        jdbc.update("UPDATE webhook_signing_secrets SET encrypted_secret = decode('00', 'hex') WHERE endpoint_id=?", endpoint);
        UUID corrupt = delivery(app, endpoint, url, "corrupt"); worker.processOnce();
        assertTerminalSigningFailure(corrupt); assertThat(captured).isEmpty(); assertThat(raw).startsWith("whsec_");
    }

    @Test void secretsAreEndpointIsolatedAndOnlyEncryptedMaterialIsStored() throws Exception {
        UUID app = application(); UUID a = endpoint(app); UUID b = endpoint(app);
        String secretA = secrets.provision(a).value(); String secretB = secrets.provision(b).value();
        assertThat(secretA).startsWith("whsec_").isNotEqualTo(secretB);
        byte[] cipher = jdbc.queryForObject("SELECT encrypted_secret FROM webhook_signing_secrets WHERE endpoint_id=?", byte[].class, a);
        byte[] nonce = jdbc.queryForObject("SELECT nonce FROM webhook_signing_secrets WHERE endpoint_id=?", byte[].class, a);
        assertThat(new String(cipher, StandardCharsets.UTF_8)).doesNotContain(secretA);
        assertThat(nonce).hasSize(12);
        assertThat(jdbc.queryForObject("SELECT key_version FROM webhook_signing_secrets WHERE endpoint_id=?", Integer.class, a)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM webhook_signing_secrets WHERE endpoint_id=?", Long.class, a)).isEqualTo(1L);
    }

    @Test void retriesAreFreshlySignedAndEndpointSecretsCannotVerifyEachOther() throws Exception {
        List<Captured> retried = new ArrayList<>(); AtomicInteger response = new AtomicInteger(500);
        String retryUrl = server(retried, response);
        UUID app = application(); UUID endpoint = endpoint(app); String secret = secrets.provision(endpoint).value();
        UUID retryDelivery = delivery(app, endpoint, retryUrl, "retry");

        worker.processOnce();
        clock.advanceSeconds(10); response.set(200); worker.processOnce();

        assertThat(retried).hasSize(2);
        Captured first = retried.get(0), second = retried.get(1);
        assertThat(first.header("X-Webhook-Delivery-Id")).isEqualTo(second.header("X-Webhook-Delivery-Id")).isEqualTo(retryDelivery.toString());
        assertThat(first.body()).isEqualTo(second.body());
        assertThat(first.timestamp()).isNotEqualTo(second.timestamp());
        assertThat(first.header("X-Webhook-Signature")).isNotEqualTo(second.header("X-Webhook-Signature"));
        assertThat(verify(secret, first.timestamp(), first.body(), first.header("X-Webhook-Signature"))).isTrue();
        assertThat(verify(secret, second.timestamp(), second.body(), second.header("X-Webhook-Signature"))).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, retryDelivery)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForList("SELECT status, http_status_code FROM webhook_delivery_attempts WHERE delivery_id=? ORDER BY attempt_number", retryDelivery))
                .extracting(row -> row.get("status") + ":" + row.get("http_status_code")).containsExactly("FAILED:500", "SUCCEEDED:200");

        server.stop(0); server = null;
        List<Captured> isolated = new ArrayList<>(); String isolatedUrl = server(isolated, new AtomicInteger(200));
        UUID endpointB = endpoint(app); String secretB = secrets.provision(endpointB).value();
        delivery(app, endpointB, isolatedUrl, "isolation"); worker.processOnce();
        Captured b = isolated.get(0);
        assertThat(verify(secretB, b.timestamp(), b.body(), b.header("X-Webhook-Signature"))).isTrue();
        assertThat(verify(secret, b.timestamp(), b.body(), b.header("X-Webhook-Signature"))).isFalse();
    }

    private void assertTerminalSigningFailure(UUID delivery) {
        assertThat(jdbc.queryForObject("SELECT status FROM webhook_deliveries WHERE id=?", String.class, delivery)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT next_retry_at FROM webhook_deliveries WHERE id=?", Instant.class, delivery)).isNull();
        assertThat(jdbc.queryForObject("SELECT error_code FROM webhook_delivery_attempts WHERE delivery_id=?", String.class, delivery)).isEqualTo("SIGNING_ERROR");
        assertThat(jdbc.queryForObject("SELECT http_status_code FROM webhook_delivery_attempts WHERE delivery_id=?", Integer.class, delivery)).isNull();
    }
    private String server(List<Captured> requests, AtomicInteger status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/hook", e -> { requests.add(Captured.from(e)); e.sendResponseHeaders(status.get(), -1); e.close(); }); server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/hook";
    }
    private UUID application() { UUID user=UUID.randomUUID(), app=UUID.randomUUID(); jdbc.update("INSERT INTO users (id,google_subject,email,display_name,status,created_at,updated_at) VALUES (?,?,? ,?,'ACTIVE',now(),now())",user,"sub"+user,"a"+user+"@x.test","A"); jdbc.update("INSERT INTO applications (id,owner_user_id,name,slug,status,environment,created_at,updated_at) VALUES (?,?,? ,?,'ACTIVE','DEVELOPMENT',now(),now())",app,user,"App","app"+app.toString().substring(0,8)); return app; }
    private UUID endpoint(UUID app) { UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO webhook_endpoints (id,application_id,name,url,status,created_at,updated_at) VALUES (?,?,'Endpoint','http://localhost:1/hook','ACTIVE',now(),now())",id,app); return id; }
    private UUID delivery(UUID app, UUID endpoint, String url, String source) { UUID event=UUID.randomUUID(), delivery=UUID.randomUUID(); jdbc.update("INSERT INTO webhook_events (id,application_id,source_event_id,event_type,payload,created_at) VALUES (?,?,?,'ai.solution.completed','{}'::jsonb,now())",event,app,source); jdbc.update("INSERT INTO webhook_deliveries (id,event_id,endpoint_id,target_url,status,created_at,updated_at) VALUES (?,?,?,?, 'PENDING',now(),now())",delivery,event,endpoint,url); return delivery; }
    private boolean verify(String rawSecret, String timestamp, byte[] body, String signature) throws Exception { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(java.util.Base64.getUrlDecoder().decode(rawSecret.substring(6)),"HmacSHA256")); mac.update(timestamp.getBytes(StandardCharsets.UTF_8)); mac.update((byte) '.'); String hex=java.util.HexFormat.of().formatHex(mac.doFinal(body)); return MessageDigest.isEqual(("v1="+hex).getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8)); }
    private record Captured(byte[] body, java.util.Map<String,List<String>> headers) { static Captured from(HttpExchange e) throws IOException { return new Captured(e.getRequestBody().readAllBytes(), e.getRequestHeaders()); } String header(String name) { return headers.get(name).get(0); } String timestamp() { return header("X-Webhook-Timestamp"); } }

    @TestConfiguration static class MutableClockConfiguration { @Bean @Primary MutableClock mutableClock() { return new MutableClock(Instant.parse("2030-01-01T00:00:00Z")); } }
    static final class MutableClock extends Clock {
        private volatile Instant instant; MutableClock(Instant instant) { this.instant = instant; }
        void set(Instant value) { instant = value; } void advanceSeconds(long seconds) { instant = instant.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; } @Override public Clock withZone(ZoneId zone) { return this; } @Override public Instant instant() { return instant; }
    }
}
