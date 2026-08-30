package com.webhookplatform.webhook.delivery;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties("webhook-platform.worker")
public record WebhookWorkerProperties(
        boolean enabled,
        @Min(1) @Max(500) int batchSize,
        @NotNull Duration pollInterval,
        @NotNull Duration connectTimeout,
        @NotNull Duration requestTimeout,
        @NotNull Duration staleProcessingTimeout) {

    @AssertTrue(message = "Worker durations must be positive and stale-processing-timeout must exceed request-timeout.")
    boolean hasSafeTimeouts() {
        return !pollInterval.isNegative() && !pollInterval.isZero()
                && !connectTimeout.isNegative() && !connectTimeout.isZero()
                && !requestTimeout.isNegative() && !requestTimeout.isZero()
                && staleProcessingTimeout.compareTo(requestTimeout) > 0;
    }
}
