package hr.emocio.recoresto.search.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Restaurant chain settings of the deck (spec 008, amendment "chains", product owner 2026-11-26): the deck keeps at most
 * one restaurant per chain. The values live in {@code application.yml} only.
 *
 * @param knownNames names of known chains; a restaurant whose normalized name starts with a normalized known name (whole
 *        words) belongs to that chain
 * @param genericHosts website hosts shared by unrelated businesses, which never group restaurants; an entry matches the
 *        host and its subdomains, and an entry ending in {@code .*} ({@code tripadvisor.*}) matches any ending
 */
@ConfigurationProperties("search.chains")
public record ChainProperties(List<String> knownNames, List<String> genericHosts) {

    public ChainProperties {
        knownNames = knownNames == null ? List.of() : List.copyOf(knownNames);
        genericHosts = genericHosts == null ? List.of() : List.copyOf(genericHosts);
    }
}
