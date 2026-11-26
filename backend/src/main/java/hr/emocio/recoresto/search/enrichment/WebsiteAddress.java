package hr.emocio.recoresto.search.enrichment;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

/**
 * A website address the backend accepts (spec 007 FR-002, T3, research R3, data-model.md "Website address"): absolute,
 * scheme {@code http} or {@code https}, a host, no user-info, and a port among the allowed ones (PO-8). The single
 * rule used by the search response ({@code GooglePlaceMapper}), the enrichment endpoint and every fetch hop.
 *
 * @param uri the address as given (trimmed)
 */
public record WebsiteAddress(URI uri) {

    /** The address, or empty when the value breaks the rule. */
    public static Optional<WebsiteAddress> parse(String value, Collection<Integer> allowedPorts) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return of(new URI(value.trim()), allowedPorts);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    /** The address, or empty when the URI breaks the rule. */
    public static Optional<WebsiteAddress> of(URI uri, Collection<Integer> allowedPorts) {
        if (!uri.isAbsolute() || uri.isOpaque() || uri.getRawUserInfo() != null || uri.getHost() == null
                || uri.getHost().isBlank()) {
            return Optional.empty();
        }
        int port = effectivePort(uri);
        return port > 0 && allowedPorts.contains(port) ? Optional.of(new WebsiteAddress(uri)) : Optional.empty();
    }

    public String host() {
        return uri.getHost();
    }

    /** The explicit port, or the default port of the scheme. */
    public int port() {
        return effectivePort(uri);
    }

    /** -1 for any scheme other than http and https. */
    private static int effectivePort(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return -1;
        }
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return scheme.equals("http") ? 80 : 443;
    }
}
