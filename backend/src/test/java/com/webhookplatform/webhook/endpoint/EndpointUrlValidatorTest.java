package com.webhookplatform.webhook.endpoint;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EndpointUrlValidatorTest {

    @Test
    void hostedModeRequiresHttpsAndRejectsUnsafeLiteralTargets() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        EndpointUrlValidator validator = new EndpointUrlValidator(environment);

        assertThatCode(() -> validator.validate("https://hooks.example.com/webhooks/ai")).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate("http://hooks.example.com/webhooks/ai"))
                .isInstanceOf(InvalidEndpointUrlException.class);
        assertThatThrownBy(() -> validator.validate("https://localhost/hooks"))
                .isInstanceOf(InvalidEndpointUrlException.class);
        assertThatThrownBy(() -> validator.validate("https://127.0.0.1/hooks"))
                .isInstanceOf(InvalidEndpointUrlException.class);
        assertThatThrownBy(() -> validator.validate("https://10.0.0.1/hooks"))
                .isInstanceOf(InvalidEndpointUrlException.class);
        assertThatThrownBy(() -> validator.validate("https://[::1]/hooks"))
                .isInstanceOf(InvalidEndpointUrlException.class);
        assertThatThrownBy(() -> validator.validate("https://[::ffff:127.0.0.1]/hooks"))
                .isInstanceOf(InvalidEndpointUrlException.class);
        assertThatThrownBy(() -> validator.validate("https://[::ffff:10.0.0.1]/hooks"))
                .isInstanceOf(InvalidEndpointUrlException.class);
        assertThatThrownBy(() -> validator.validate("https://[::ffff:192.168.1.1]/hooks"))
                .isInstanceOf(InvalidEndpointUrlException.class);
        assertThatThrownBy(() -> validator.validate("https://[::ffff:172.16.0.1]/hooks"))
                .isInstanceOf(InvalidEndpointUrlException.class);
        assertThatCode(() -> validator.validate("https://[2001:4860:4860::8888]/hooks"))
                .doesNotThrowAnyException();
    }

    @Test
    void localDevelopmentAllowsOnlyLocalHttpExceptions() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        EndpointUrlValidator validator = new EndpointUrlValidator(environment);

        assertThatCode(() -> validator.validate("http://localhost:8081/hooks")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate("http://127.0.0.1:8081/hooks")).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate("http://hooks.example.com/hooks"))
                .isInstanceOf(InvalidEndpointUrlException.class);
    }

    @Test
    void rejectsCredentialsFragmentsAndMalformedUrls() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        EndpointUrlValidator validator = new EndpointUrlValidator(environment);

        assertThatThrownBy(() -> validator.validate("https://user:password@hooks.example.com/path"))
                .isInstanceOf(InvalidEndpointUrlException.class);
        assertThatThrownBy(() -> validator.validate("https://hooks.example.com/path#fragment"))
                .isInstanceOf(InvalidEndpointUrlException.class);
        assertThatThrownBy(() -> validator.validate("file:///tmp/hook"))
                .isInstanceOf(InvalidEndpointUrlException.class);
        assertThatThrownBy(() -> validator.validate("not-a-url"))
                .isInstanceOf(InvalidEndpointUrlException.class);
    }
}
