package com.webhookplatform.webhook.delivery;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.hc.client5.http.DnsResolver;
class ValidatingDnsResolver implements DnsResolver {

    private final DestinationAddressPolicy destinationAddressPolicy;
    private final boolean allowDevHttpLocalhost;

    ValidatingDnsResolver(DestinationAddressPolicy destinationAddressPolicy, boolean allowDevHttpLocalhost) {
        this.destinationAddressPolicy = destinationAddressPolicy;
        this.allowDevHttpLocalhost = allowDevHttpLocalhost;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        destinationAddressPolicy.validate(host, addresses, allowDevHttpLocalhost);
        return addresses;
    }

    @Override
    public String resolveCanonicalHostname(String host) {
        return host;
    }
}
