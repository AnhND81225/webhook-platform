package com.webhookplatform.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

class WebhookRetryPolicyTest {

    private final WebhookRetryPolicy policy = new WebhookRetryPolicy(new WebhookRetryProperties(
            true, 5, List.of(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10))));

    @Test
    void retriesOnlyApprovedHttpAndTransientErrorOutcomesBeforeTheMaximumAttempt() {
        for (int status : new int[] {408, 429, 500, 502, 503, 504}) {
            assertThat(policy.forFailure(1, status, WebhookDeliveryAttemptErrorCode.HTTP_ERROR).shouldRetry()).isTrue();
        }
        for (int status : new int[] {302, 400, 401, 403, 404, 405, 410, 422}) {
            assertThat(policy.forFailure(1, status, WebhookDeliveryAttemptErrorCode.HTTP_ERROR).shouldRetry()).isFalse();
        }
        for (WebhookDeliveryAttemptErrorCode code : new WebhookDeliveryAttemptErrorCode[] {
                WebhookDeliveryAttemptErrorCode.DNS_ERROR, WebhookDeliveryAttemptErrorCode.CONNECTION_ERROR,
                WebhookDeliveryAttemptErrorCode.TIMEOUT }) {
            assertThat(policy.forFailure(1, null, code).shouldRetry()).isTrue();
        }
        for (WebhookDeliveryAttemptErrorCode code : new WebhookDeliveryAttemptErrorCode[] {
                WebhookDeliveryAttemptErrorCode.TLS_ERROR, WebhookDeliveryAttemptErrorCode.SSRF_REJECTED,
                WebhookDeliveryAttemptErrorCode.IO_ERROR, WebhookDeliveryAttemptErrorCode.UNEXPECTED_ERROR }) {
            assertThat(policy.forFailure(1, null, code).shouldRetry()).isFalse();
        }
    }

    @Test
    void usesAttemptNumberForBackoffAndNeverSchedulesAfterTheFifthAttempt() {
        assertThat(policy.forFailure(1, 500, WebhookDeliveryAttemptErrorCode.HTTP_ERROR).delay()).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.forFailure(2, 500, WebhookDeliveryAttemptErrorCode.HTTP_ERROR).delay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.forFailure(3, 500, WebhookDeliveryAttemptErrorCode.HTTP_ERROR).delay()).isEqualTo(Duration.ofMinutes(2));
        assertThat(policy.forFailure(4, 500, WebhookDeliveryAttemptErrorCode.HTTP_ERROR).delay()).isEqualTo(Duration.ofMinutes(10));
        assertThat(policy.forFailure(5, 500, WebhookDeliveryAttemptErrorCode.HTTP_ERROR).shouldRetry()).isFalse();
        assertThat(policy.forAbandonedAttempt(5).shouldRetry()).isFalse();
    }

    @Test
    void disabledRetryMakesOtherwiseRetryableFailuresTerminal() {
        WebhookRetryPolicy disabled = new WebhookRetryPolicy(new WebhookRetryProperties(
                false, 5, List.of(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10))));
        assertThat(disabled.forFailure(1, 503, WebhookDeliveryAttemptErrorCode.HTTP_ERROR).shouldRetry()).isFalse();
    }
}
