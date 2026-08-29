package com.webhookplatform.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BootstrapTest {

    @Test
    void javaRuntimeMeetsProjectBaseline() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(17);
    }
}
