package hr.emocio.recoresto.search.restaurant;

import hr.emocio.recoresto.search.config.ChainProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The keys telling which chain a restaurant belongs to (spec 008, amendment "chains", product owner 2026-11-26). Two
 * restaurants sharing any key are the same chain: the same website host (lower case, without a leading {@code www.},
 * except the generic hosts of {@link ChainProperties#genericHosts()}), the same normalized name, or the same known chain
 * name of {@link ChainProperties#knownNames()} starting their normalized name.
 */
final class ChainIdentity {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SUFFIX = Pattern.compile(" - | \\| |\\(");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private final List<String> knownNames;
    private final List<String> genericHosts;

    ChainIdentity(ChainProperties properties) {
        this.knownNames = properties.knownNames().stream().map(ChainIdentity::normalize)
                .filter(name -> !name.isEmpty()).distinct().toList();
        this.genericHosts = properties.genericHosts().stream().map(host -> host.trim().toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty()).toList();
    }

    /** The chain keys of a restaurant; empty when it has neither a usable website nor a usable name. */
    List<String> keys(Restaurant restaurant) {
        List<String> keys = new ArrayList<>(3);
        String host = host(restaurant.website());
        if (host != null && !isGeneric(host)) {
            keys.add("host:" + host);
        }
        String name = normalize(restaurant.name());
        if (!name.isEmpty()) {
            keys.add("name:" + name);
            knownNames.stream().filter(known -> name.equals(known) || name.startsWith(known + " ")).findFirst()
                    .ifPresent(known -> keys.add("chain:" + known));
        }
        return keys;
    }

    /**
     * Lower case, accents removed, the part after the first " - ", " | " or "(" dropped, punctuation removed and spaces
     * collapsed: "Big Mamma - East Mamma" gives "big mamma", "O'Tacos" gives "o tacos".
     */
    static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String plain = MARKS.matcher(Normalizer.normalize(name, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT);
        String head = SUFFIX.split(plain, 2)[0];
        return NOT_ALPHANUMERIC.matcher(head).replaceAll(" ").trim();
    }

    /** Lower-case host without a leading "www.", read with "http://" when the address has no scheme; else null. */
    static String host(String website) {
        if (website == null || website.isBlank()) {
            return null;
        }
        String value = website.trim();
        try {
            URI uri = new URI(value.contains("://") ? value : "http://" + value);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** "facebook.com" matches it and its subdomains; "tripadvisor.*" matches "tripadvisor." anywhere as a label. */
    private boolean isGeneric(String host) {
        for (String generic : genericHosts) {
            if (generic.endsWith(".*")) {
                String label = generic.substring(0, generic.length() - 1);
                if (host.startsWith(label) || host.contains("." + label)) {
                    return true;
                }
            } else if (host.equals(generic) || host.endsWith("." + generic)) {
                return true;
            }
        }
        return false;
    }
}
