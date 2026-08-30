package com.webhookplatform.webhook.delivery;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties("webhook-platform.retry")
public record WebhookRetryProperties(
        boolean enabled,
        @Min(1) int maxAttempts,
        @NotNull List<@NotNull Duration> delays) {

    @AssertTrue(message = "Retry delays must be positive and contain exactly max-attempts minus one entries.")
    boolean hasValidDelays() {
        return delays.size() == maxAttempts - 1
                && delays.stream().allMatch(delay -> !delay.isNegative() && !delay.isZero());
    }
}
