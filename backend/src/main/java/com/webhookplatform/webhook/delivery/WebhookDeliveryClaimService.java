package com.webhookplatform.webhook.delivery;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WebhookDeliveryClaimService {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final WebhookWorkerProperties properties;
    private final WebhookRetryPolicy retryPolicy;

    WebhookDeliveryClaimService(JdbcTemplate jdbcTemplate, Clock clock, WebhookWorkerProperties properties,
            WebhookRetryPolicy retryPolicy) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.properties = properties;
        this.retryPolicy = retryPolicy;
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
                       OR (status = 'RETRY_SCHEDULED' AND next_retry_at <= ?)
                    ORDER BY COALESCE(next_retry_at, created_at), id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                ), claimed AS (
                    UPDATE webhook_deliveries d
                    SET status = 'PROCESSING', processing_started_at = ?, claim_token = ?, next_retry_at = NULL, updated_at = ?
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
                        resultSet.getString(9)), timestamp(now), batchSize, timestamp(now), claimToken, timestamp(now));
    }

    @Transactional
    public int recoverStaleProcessing() {
        Instant now = clock.instant();
        List<StaleClaim> staleClaims = jdbcTemplate.query("""
                SELECT id, claim_token
                FROM webhook_deliveries
                WHERE status = 'PROCESSING' AND processing_started_at < ?
                FOR UPDATE
                """, (resultSet, rowNum) -> new StaleClaim(
                        resultSet.getObject("id", UUID.class), resultSet.getObject("claim_token", UUID.class)),
                timestamp(now.minus(properties.staleProcessingTimeout())));
        for (StaleClaim stale : staleClaims) {
            Optional<Integer> inProgressAttempt = jdbcTemplate.query("""
                    SELECT attempt_number
                    FROM webhook_delivery_attempts
                    WHERE delivery_id = ? AND claim_token = ? AND status = 'IN_PROGRESS'
                    FOR UPDATE
                    """, resultSet -> resultSet.next() ? Optional.of(resultSet.getInt(1)) : Optional.empty(),
                    stale.deliveryId(), stale.claimToken());
            if (inProgressAttempt.isPresent()) {
                int attemptNumber = inProgressAttempt.orElseThrow();
                jdbcTemplate.update("""
                    UPDATE webhook_delivery_attempts
                    SET status = 'ABANDONED', completed_at = ?
                    WHERE delivery_id = ? AND claim_token = ? AND status = 'IN_PROGRESS'
                    """, timestamp(now), stale.deliveryId(), stale.claimToken());
                WebhookRetryDecision decision = retryPolicy.forAbandonedAttempt(attemptNumber);
                jdbcTemplate.update("""
                    UPDATE webhook_deliveries
                    SET status = ?, processing_started_at = NULL, claim_token = NULL, next_retry_at = ?, updated_at = ?
                    WHERE id = ? AND status = 'PROCESSING' AND claim_token = ?
                    """, decision.shouldRetry() ? WebhookDeliveryStatus.RETRY_SCHEDULED.name() : WebhookDeliveryStatus.FAILED.name(),
                        decision.shouldRetry() ? timestamp(now.plus(decision.delay())) : null, timestamp(now), stale.deliveryId(), stale.claimToken());
            } else {
                jdbcTemplate.update("""
                        UPDATE webhook_deliveries
                        SET status = 'PENDING', processing_started_at = NULL, claim_token = NULL, next_retry_at = NULL, updated_at = ?
                        WHERE id = ? AND status = 'PROCESSING' AND claim_token = ?
                        """, timestamp(now), stale.deliveryId(), stale.claimToken());
            }
        }
        return staleClaims.size();
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

    private record StaleClaim(UUID deliveryId, UUID claimToken) {
    }
}
