package hr.emocio.recoresto.search.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Overpass API settings and the bounds applied to what the language model may query (plan.md O11, O14). The values
 * in {@code application.yml} are not validated by the team yet.
 *
 * @param baseUrl full URL of the Overpass interpreter endpoint
 * @param timeoutSeconds HTTP read timeout of one Overpass call
 * @param maxResponseBytes largest Overpass answer given back to the model
 * @param maxQueryChars longest query accepted from the model
 * @param maxCalls largest number of Overpass calls for one search
 * @param fixtureFile development only: path of a JSON file returned instead of calling Overpass (data NOT real);
 *        empty in normal use
 * @param retryDelaysSeconds waits before each new attempt after an HTTP 502 ("at capacity"); the number of attempts
 *        is one more than the number of delays; empty means a single attempt
 */
@ConfigurationProperties("overpass")
public record OverpassProperties(String baseUrl, int timeoutSeconds, int maxResponseBytes, int maxQueryChars,
        int maxCalls, String fixtureFile, List<Integer> retryDelaysSeconds) {

    public OverpassProperties {
        retryDelaysSeconds = retryDelaysSeconds == null ? List.of() : List.copyOf(retryDelaysSeconds);
    }
}
