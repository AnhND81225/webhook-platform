package com.webhookplatform.webhook.endpoint;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class EndpointUrlValidator {

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private final Environment environment;

    public EndpointUrlValidator(Environment environment) {
        this.environment = environment;
    }

    public void validate(String value) {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw new InvalidEndpointUrlException();
        }
        if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawFragment() != null) {
            throw new InvalidEndpointUrlException();
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean localDevelopment = environment.acceptsProfiles(Profiles.of("dev"));

        if ("https".equals(scheme)) {
            if (!localDevelopment && isUnsafeLiteralOrLocalhost(host)) {
                throw new InvalidEndpointUrlException();
            }
            return;
        }
        if (localDevelopment && "http".equals(scheme) && isAllowedLocalHost(host)) {
            return;
        }
        throw new InvalidEndpointUrlException();
    }

    private boolean isAllowedLocalHost(String host) {
        return "localhost".equals(host) || "127.0.0.1".equals(host);
    }

    private boolean isUnsafeLiteralOrLocalhost(String host) {
        if ("localhost".equals(host) || host.endsWith(".localhost")) {
            return true;
        }
        String unbracketed = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
        return isUnsafeIpv4(unbracketed) || isUnsafeIpv6(unbracketed);
    }

    private boolean isUnsafeIpv4(String host) {
        var matcher = IPV4.matcher(host);
        if (!matcher.matches()) {
            return false;
        }
        int first = parseOctet(matcher.group(1));
        int second = parseOctet(matcher.group(2));
        int third = parseOctet(matcher.group(3));
        int fourth = parseOctet(matcher.group(4));
        if (first < 0 || second < 0 || third < 0 || fourth < 0) {
            return true;
        }
        return first == 0 || first == 10 || first == 127
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
    }

    private int parseOctet(String value) {
        int octet = Integer.parseInt(value);
        return octet <= 255 ? octet : -1;
    }

    private boolean isUnsafeIpv6(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        String embeddedIpv4 = embeddedIpv4Literal(normalized);
        if (embeddedIpv4 != null) {
            return isUnsafeIpv4(embeddedIpv4);
        }
        return "::1".equals(normalized)
                || normalized.startsWith("fc")
                || normalized.startsWith("fd")
                || normalized.startsWith("fe8")
                || normalized.startsWith("fe9")
                || normalized.startsWith("fea")
                || normalized.startsWith("feb");
    }

    private String embeddedIpv4Literal(String host) {
        if (host.startsWith("::ffff:")) {
            String candidate = host.substring("::ffff:".length());
            return IPV4.matcher(candidate).matches() ? candidate : null;
        }
        if (host.startsWith("::")) {
            String candidate = host.substring("::".length());
            return IPV4.matcher(candidate).matches() ? candidate : null;
        }
        return null;
    }
}
