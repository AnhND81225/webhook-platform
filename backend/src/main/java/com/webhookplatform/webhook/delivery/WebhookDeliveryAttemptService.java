package com.webhookplatform.webhook.delivery;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists durable attempt history while keeping outbound HTTP outside database transactions. */
@Service
class WebhookDeliveryAttemptService {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final WebhookRetryPolicy retryPolicy;

    WebhookDeliveryAttemptService(JdbcTemplate jdbcTemplate, Clock clock, WebhookRetryPolicy retryPolicy) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.retryPolicy = retryPolicy;
    }

    @Transactional
    Optional<StartedWebhookDeliveryAttempt> startAttempt(ClaimedDelivery delivery) {
        try {
            jdbcTemplate.queryForObject("""
                    SELECT id FROM webhook_deliveries
                    WHERE id = ? AND status = 'PROCESSING' AND claim_token = ?
                    FOR UPDATE
                    """, UUID.class, delivery.deliveryId(), delivery.claimToken());
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }

        int attemptNumber = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(attempt_number), 0) + 1
                FROM webhook_delivery_attempts WHERE delivery_id = ?
                """, Integer.class, delivery.deliveryId());
        UUID attemptId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO webhook_delivery_attempts
                    (id, delivery_id, attempt_number, claim_token, status, started_at)
                VALUES (?, ?, ?, ?, 'IN_PROGRESS', ?)
                """, attemptId, delivery.deliveryId(), attemptNumber, delivery.claimToken(), timestamp(clock.instant()));
        return Optional.of(new StartedWebhookDeliveryAttempt(attemptId, delivery.deliveryId(), delivery.claimToken(), attemptNumber));
    }

    @Transactional
    boolean completeAttemptAndFinalize(StartedWebhookDeliveryAttempt attempt, WebhookDeliveryStatus deliveryStatus,
            Integer httpStatusCode, WebhookDeliveryAttemptErrorCode errorCode, long durationMs) {
        if (deliveryStatus != WebhookDeliveryStatus.DELIVERED && deliveryStatus != WebhookDeliveryStatus.FAILED) {
            throw new IllegalArgumentException("M7 can finalize only DELIVERED or FAILED deliveries.");
        }
        try {
            jdbcTemplate.queryForObject("""
                    SELECT id FROM webhook_deliveries
                    WHERE id = ? AND status = 'PROCESSING' AND claim_token = ?
                    FOR UPDATE
                    """, UUID.class, attempt.deliveryId(), attempt.claimToken());
        } catch (EmptyResultDataAccessException exception) {
            return false;
        }

        Instant now = clock.instant();
        WebhookDeliveryAttemptStatus attemptStatus = deliveryStatus == WebhookDeliveryStatus.DELIVERED
                ? WebhookDeliveryAttemptStatus.SUCCEEDED : WebhookDeliveryAttemptStatus.FAILED;
        int completed = jdbcTemplate.update("""
                UPDATE webhook_delivery_attempts
                SET status = ?, completed_at = ?, duration_ms = ?, http_status_code = ?, error_code = ?
                WHERE id = ? AND delivery_id = ? AND claim_token = ? AND status = 'IN_PROGRESS'
                """, attemptStatus.name(), timestamp(now), Math.max(0, durationMs), httpStatusCode,
                errorCode == null ? null : errorCode.name(), attempt.attemptId(), attempt.deliveryId(), attempt.claimToken());
        if (completed != 1) {
            throw new IllegalStateException("Claim-owned delivery attempt was not available for completion.");
        }
        int finalized = jdbcTemplate.update("""
                UPDATE webhook_deliveries
                SET status = ?, processing_started_at = NULL, claim_token = NULL, updated_at = ?
                WHERE id = ? AND status = 'PROCESSING' AND claim_token = ?
                """, deliveryStatus.name(), timestamp(now), attempt.deliveryId(), attempt.claimToken());
        if (finalized != 1) {
            throw new IllegalStateException("Claim-owned delivery was not available for finalization.");
        }
        return true;
    }

    @Transactional
    boolean completeAttemptAndResolve(StartedWebhookDeliveryAttempt attempt, boolean successful,
            Integer httpStatusCode, WebhookDeliveryAttemptErrorCode errorCode, long durationMs) {
        try {
            jdbcTemplate.queryForObject("""
                    SELECT id FROM webhook_deliveries
                    WHERE id = ? AND status = 'PROCESSING' AND claim_token = ?
                    FOR UPDATE
                    """, UUID.class, attempt.deliveryId(), attempt.claimToken());
        } catch (EmptyResultDataAccessException exception) {
            return false;
        }

        Instant now = clock.instant();
        WebhookRetryDecision decision = successful ? WebhookRetryDecision.terminal()
                : retryPolicy.forFailure(attempt.attemptNumber(), httpStatusCode, errorCode);
        WebhookDeliveryStatus deliveryStatus = successful ? WebhookDeliveryStatus.DELIVERED
                : decision.shouldRetry() ? WebhookDeliveryStatus.RETRY_SCHEDULED : WebhookDeliveryStatus.FAILED;
        WebhookDeliveryAttemptStatus attemptStatus = successful ? WebhookDeliveryAttemptStatus.SUCCEEDED
                : WebhookDeliveryAttemptStatus.FAILED;
        int completed = jdbcTemplate.update("""
                UPDATE webhook_delivery_attempts
                SET status = ?, completed_at = ?, duration_ms = ?, http_status_code = ?, error_code = ?
                WHERE id = ? AND delivery_id = ? AND claim_token = ? AND status = 'IN_PROGRESS'
                """, attemptStatus.name(), timestamp(now), Math.max(0, durationMs), httpStatusCode,
                errorCode == null ? null : errorCode.name(), attempt.attemptId(), attempt.deliveryId(), attempt.claimToken());
        if (completed != 1) {
            throw new IllegalStateException("Claim-owned delivery attempt was not available for completion.");
        }
        int finalized = jdbcTemplate.update("""
                UPDATE webhook_deliveries
                SET status = ?, processing_started_at = NULL, claim_token = NULL, next_retry_at = ?, updated_at = ?
                WHERE id = ? AND status = 'PROCESSING' AND claim_token = ?
                """, deliveryStatus.name(), decision.shouldRetry() ? timestamp(now.plus(decision.delay())) : null,
                timestamp(now), attempt.deliveryId(), attempt.claimToken());
        if (finalized != 1) {
            throw new IllegalStateException("Claim-owned delivery was not available for finalization.");
        }
        return true;
    }

    private OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
