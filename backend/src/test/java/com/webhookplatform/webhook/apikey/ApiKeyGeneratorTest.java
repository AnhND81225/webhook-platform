package com.webhookplatform.webhook.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.webhookplatform.webhook.application.ApplicationEnvironment;

class ApiKeyGeneratorTest {

    private final ApiKeyGenerator generator = new ApiKeyGenerator();

    @Test
    void generatesProductionKeyWithThirtyTwoRandomBytesAndMatchingDigest() {
        ApiKeyGenerator.GeneratedApiKey generated = generator.generate(ApplicationEnvironment.PRODUCTION);

        assertThat(generated.rawKey()).startsWith("whk_live_").hasSize(52);
        assertThat(generated.prefix()).isEqualTo(generated.rawKey().substring(0, 13));
        assertThat(Base64.getUrlDecoder().decode(generated.rawKey().substring(9))).hasSize(32);
        assertThat(generated.hash())
                .matches("[0-9a-f]{64}")
                .isEqualTo(ApiKeyGenerator.sha256(generated.rawKey()));
    }

    @Test
    void generatesDevelopmentKeyWithTestMarker() {
        ApiKeyGenerator.GeneratedApiKey generated = generator.generate(ApplicationEnvironment.DEVELOPMENT);

        assertThat(generated.rawKey()).startsWith("whk_test_").hasSize(52);
        assertThat(generated.prefix()).isEqualTo(generated.rawKey().substring(0, 13));
    }

    @Test
    void generatesDistinctCredentials() {
        Set<String> generated = new HashSet<>();

        for (int index = 0; index < 100; index++) {
            generated.add(generator.generate(ApplicationEnvironment.PRODUCTION).rawKey());
        }

        assertThat(generated).hasSize(100);
    }
}
