package hr.emocio.recoresto.search.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Places API (New) settings (spec 008, data-model.md "Configuration"). The values live in
 * {@code application.yml}, each with the decision it comes from.
 *
 * @param baseUrl base address of the Places API (New)
 * @param apiKey key sent in the {@code X-Goog-Api-Key} header only; blank is accepted at startup (every search is then
 *        "service unavailable"); never logged, see {@link #toString()}
 * @param timeoutSeconds connect and read timeout of one Google request
 * @param retryDelaysSeconds waits before each new attempt after a transient failure; empty means a single attempt
 * @param maxResults places asked for per restaurant search, one page of Text Search (1-20)
 * @param photoMaxWidthPx width of the photos fetched for the cards
 * @param languageCode language of the Google answers
 * @param regionCode region of the Google answers
 * @param fixtureFile development only: path of a JSON file returned instead of calling Google (data NOT real); empty
 *        in normal use
 */
@ConfigurationProperties("google-places")
public record GooglePlacesProperties(String baseUrl, String apiKey, int timeoutSeconds,
        List<Integer> retryDelaysSeconds, int maxResults, int photoMaxWidthPx, String languageCode, String regionCode,
        String fixtureFile) {

    public GooglePlacesProperties {
        retryDelaysSeconds = retryDelaysSeconds == null ? List.of() : List.copyOf(retryDelaysSeconds);
    }

    /** Whether a key is configured. */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Never shows the key (FR-013). */
    @Override
    public String toString() {
        return "GooglePlacesProperties[baseUrl=" + baseUrl + ", apiKey=" + (hasApiKey() ? "***" : "<missing>")
                + ", timeoutSeconds=" + timeoutSeconds + ", retryDelaysSeconds=" + retryDelaysSeconds
                + ", maxResults=" + maxResults + ", photoMaxWidthPx=" + photoMaxWidthPx + ", languageCode="
                + languageCode + ", regionCode=" + regionCode + ", fixtureFile=" + fixtureFile + "]";
    }
}
