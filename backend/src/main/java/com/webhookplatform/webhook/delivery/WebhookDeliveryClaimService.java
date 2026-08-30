package com.webhookplatform.webhook.delivery;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WebhookDeliveryClaimService {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final WebhookWorkerProperties properties;

    WebhookDeliveryClaimService(JdbcTemplate jdbcTemplate, Clock clock, WebhookWorkerProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional
    public List<ClaimedDelivery> claimPending(int batchSize) {
        Instant now = clock.instant();
        UUID claimToken = UUID.randomUUID();
        return jdbcTemplate.query("""
                WITH candidates AS (
                    SELECT id
                    FROM webhook_deliveries
                    WHERE status = 'PENDING'
                    ORDER BY created_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                ), claimed AS (
                    UPDATE webhook_deliveries d
                    SET status = 'PROCESSING', processing_started_at = ?, claim_token = ?, updated_at = ?
                    FROM candidates c
                    WHERE d.id = c.id
                    RETURNING d.id, d.event_id, d.endpoint_id, d.target_url, d.claim_token
                )
                SELECT c.id, c.event_id, c.endpoint_id, c.claim_token, c.target_url,
                       e.source_event_id, e.event_type, e.created_at, e.payload::text
                FROM claimed c
                JOIN webhook_events e ON e.id = c.event_id
                ORDER BY c.id
                """, (resultSet, rowNum) -> new ClaimedDelivery(
                        resultSet.getObject(1, UUID.class),
                        resultSet.getObject(2, UUID.class),
                        resultSet.getObject(3, UUID.class),
                        resultSet.getObject(4, UUID.class),
                        resultSet.getString(5),
                        resultSet.getString(6),
                        resultSet.getString(7),
                        resultSet.getObject(8, OffsetDateTime.class).toInstant(),
                        resultSet.getString(9)), batchSize, timestamp(now), claimToken, timestamp(now));
    }

    @Transactional
    public int recoverStaleProcessing() {
        Instant now = clock.instant();
        return jdbcTemplate.update("""
                UPDATE webhook_deliveries
                SET status = 'PENDING', processing_started_at = NULL, claim_token = NULL, updated_at = ?
                WHERE status = 'PROCESSING' AND processing_started_at < ?
                """, timestamp(now), timestamp(now.minus(properties.staleProcessingTimeout())));
    }

    @Transactional
    public boolean finalizeClaim(UUID deliveryId, UUID claimToken, WebhookDeliveryStatus finalStatus) {
        if (finalStatus != WebhookDeliveryStatus.DELIVERED && finalStatus != WebhookDeliveryStatus.FAILED) {
            throw new IllegalArgumentException("M6 can finalize only DELIVERED or FAILED deliveries.");
        }
        return jdbcTemplate.update("""
                UPDATE webhook_deliveries
                SET status = ?, processing_started_at = NULL, claim_token = NULL, updated_at = ?
                WHERE id = ? AND status = 'PROCESSING' AND claim_token = ?
                """, finalStatus.name(), timestamp(clock.instant()), deliveryId, claimToken) == 1;
    }

    private OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
