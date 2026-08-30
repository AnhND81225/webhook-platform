package com.webhookplatform.webhook.delivery;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
class DestinationAddressPolicy {

    private final Environment environment;

    DestinationAddressPolicy(Environment environment) {
        this.environment = environment;
    }

    void validate(String host, InetAddress[] addresses, boolean allowDevHttpLocalhost) {
        if (addresses.length == 0) {
            throw new UnsafeWebhookDestinationException();
        }
        boolean localDevTarget = allowDevHttpLocalhost && environment.acceptsProfiles(Profiles.of("dev"))
                && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host));
        for (InetAddress address : addresses) {
            if (!localDevTarget && isUnsafe(address)) {
                throw new UnsafeWebhookDestinationException();
            }
        }
    }

    private boolean isUnsafe(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first == 0
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 198 && (second == 18 || second == 19));
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            return (first & 0xfe) == 0xfc || (first == 0xfe && (Byte.toUnsignedInt(bytes[1]) & 0xc0) == 0x80);
        }
        return true;
    }
}
