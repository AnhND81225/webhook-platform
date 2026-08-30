package com.webhookplatform.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DestinationAddressPolicyTest {

    @Test
    void hostedRuntimeRejectsUnsafeIpv4Ipv6AndMappedIpv6Addresses() throws Exception {
        DestinationAddressPolicy policy = new DestinationAddressPolicy(environment("prod"));
        for (String value : new String[] {"127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1", "169.254.169.254", "::1", "fd00::1", "fe80::1", "::ffff:127.0.0.1"}) {
            assertThatThrownBy(() -> policy.validate("example.test", new InetAddress[] {InetAddress.getByName(value)}, false))
                    .isInstanceOf(UnsafeWebhookDestinationException.class);
        }
    }

    @Test
    void rejectsHostnameWhenAnyResolvedAddressIsUnsafeButAllowsPublicAddress() throws Exception {
        DestinationAddressPolicy policy = new DestinationAddressPolicy(environment("prod"));
        assertThatThrownBy(() -> policy.validate("mixed.example.test", new InetAddress[] {InetAddress.getByName("8.8.8.8"), InetAddress.getByName("10.0.0.1")}, false))
                .isInstanceOf(UnsafeWebhookDestinationException.class);
        assertThatCode(() -> policy.validate("public.example.test", new InetAddress[] {InetAddress.getByName("8.8.8.8")}, false))
                .doesNotThrowAnyException();
    }

    @Test
    void devAllowsOnlyControlledLocalTargets() throws Exception {
        DestinationAddressPolicy policy = new DestinationAddressPolicy(environment("dev"));
        assertThatCode(() -> policy.validate("localhost", new InetAddress[] {InetAddress.getByName("127.0.0.1")}, true))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate("private.example.test", new InetAddress[] {InetAddress.getByName("10.0.0.1")}, true))
                .isInstanceOf(UnsafeWebhookDestinationException.class);
        assertThatThrownBy(() -> policy.validate("localhost", new InetAddress[] {InetAddress.getByName("127.0.0.1")}, false))
                .isInstanceOf(UnsafeWebhookDestinationException.class);
    }

    private MockEnvironment environment(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }
}
