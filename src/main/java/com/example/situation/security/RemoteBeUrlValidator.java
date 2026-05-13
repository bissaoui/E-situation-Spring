package com.example.situation.security;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class RemoteBeUrlValidator {

    private final List<String> allowedHosts;

    public RemoteBeUrlValidator(
        @Value("${app.be.allowed-remote-hosts:1drv.ms,onedrive.live.com,sharepoint.com}") String allowedHosts
    ) {
        this.allowedHosts = Arrays.stream(allowedHosts.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(value -> value.toLowerCase(Locale.ROOT))
            .toList();
    }

    public URI validate(String rawUrl, boolean download) {
        URI uri = toUri(rawUrl);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);

        if (!"https".equals(scheme) || host.isBlank() || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Remote file URL is not allowed.");
        }
        if (isPrivateAddress(host) || allowedHosts.stream().noneMatch(allowed -> host.equals(allowed) || host.endsWith("." + allowed))) {
            throw new IllegalArgumentException("Remote file URL is not allowed.");
        }

        return UriComponentsBuilder.fromUri(uri)
            .replaceQueryParam("download", download ? "1" : "0")
            .build(true)
            .toUri();
    }

    public String hostOf(String rawUrl) {
        URI uri = toUri(rawUrl);
        return uri.getHost() == null ? "-" : uri.getHost().toLowerCase(Locale.ROOT);
    }

    private static URI toUri(String rawUrl) {
        try {
            return new URI(rawUrl == null ? "" : rawUrl.trim());
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Remote file URL is not allowed.");
        }
    }

    private static boolean isPrivateAddress(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress()
                || address.isSiteLocalAddress();
        } catch (Exception ex) {
            return true;
        }
    }
}
