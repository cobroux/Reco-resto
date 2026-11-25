package hr.emocio.recoresto.search.restaurant;

import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.config.OverpassProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs an Overpass query written by the language model. A query that is refused or an answer that cannot be used
 * gives an {@link OverpassRefusal}, whose message is handed back to the model; a service failure gives
 * {@code SERVICE_UNAVAILABLE}.
 */
@Component
public class OverpassClient {

    /** The query was refused or the answer is unusable; the message is meant for the language model. */
    public static class OverpassRefusal extends RuntimeException {

        public OverpassRefusal(String message) {
            super(message);
        }
    }

    /**
     * Public Overpass instances refuse anonymous default clients (HTTP 406 observed with curl's default agent on
     * 2026-11-25); a generic application name, without personal details, identifies this one.
     */
    static final String USER_AGENT = "Reco-Resto/0.1";

    private static final Logger log = LoggerFactory.getLogger(OverpassClient.class);

    /** The error lines of the HTML page Overpass answers with to a query it cannot run. */
    private static final Pattern OVERPASS_ERROR = Pattern.compile("<strong[^>]*>Error</strong>:\s*([^<]*)");

    private final RestClient client;
    private final OverpassProperties properties;
    private final JsonMapper mapper;
    private final LongConsumer sleeper;

    OverpassClient(RestClient client, OverpassProperties properties, JsonMapper mapper, LongConsumer sleeper) {
        this.client = client;
        this.properties = properties;
        this.mapper = mapper;
        this.sleeper = sleeper;
    }

    @Autowired
    public OverpassClient(RestClient.Builder builder, OverpassProperties properties, JsonMapper mapper) {
        this(builder.requestFactory(timeouts(properties)).build(), properties, mapper, OverpassClient::sleep);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "Interrupted while waiting for Overpass", e);
        }
    }

    private static SimpleClientHttpRequestFactory timeouts(OverpassProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        return factory;
    }

    public JsonNode query(String query) {
        if (query == null || query.isBlank()) {
            throw new OverpassRefusal("The query is empty.");
        }
        if (query.length() > properties.maxQueryChars()) {
            throw new OverpassRefusal("The query is longer than " + properties.maxQueryChars()
                    + " characters; make it shorter.");
        }
        if (!query.contains("[out:json")) {
            throw new OverpassRefusal("The query must start with the setting [out:json] (for example "
                    + "[out:json][timeout:25];).");
        }
        // Read raw bytes: Overpass may answer with a generic content type (application/octet-stream) that the
        // String converter refuses.
        byte[] bytes = hasFixture() ? readFixture() : fetch(query);
        if (bytes == null || bytes.length > properties.maxResponseBytes()) {
            throw new OverpassRefusal("The answer is too large (limit " + properties.maxResponseBytes()
                    + " bytes); narrow the query (smaller radius, fewer tags, out limit).");
        }
        String body = new String(bytes, StandardCharsets.UTF_8);
        try {
            JsonNode answer = mapper.readTree(body);
            if (!answer.path("elements").isArray()) {
                throw new OverpassRefusal("Overpass did not return elements: " + abbreviate(body));
            }
            return answer;
        } catch (JacksonException e) {
            throw new OverpassRefusal("Overpass did not return JSON: " + abbreviate(body));
        }
    }

    /**
     * One call, with new attempts after the configured waits when the server answers 502 ("at capacity", which the
     * public instances recommend retrying with a growing wait). Any other failure is final.
     */
    private byte[] fetch(String query) {
        List<Integer> delays = properties.retryDelaysSeconds();
        for (int attempt = 0;; attempt++) {
            try {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("data", query);
                return client.post().uri(properties.baseUrl()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("User-Agent", USER_AGENT).body(form).retrieve().body(byte[].class);
            } catch (HttpClientErrorException.BadRequest e) {
                // The query itself is wrong (for example an invalid regular expression): the model can correct it.
                log.info("Overpass rejected the query (HTTP 400)");
                throw new OverpassRefusal(badRequestMessage(e.getResponseBodyAsString()));
            } catch (RestClientException e) {
                boolean retry = attempt < delays.size() && isBadGateway(e);
                log.warn("Overpass call failed (attempt {} of {}): {} (cause: {})", attempt + 1, delays.size() + 1,
                        e.getMessage(), NestedExceptionUtils.getMostSpecificCause(e));
                if (!retry) {
                    throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "Overpass is unavailable", e);
                }
                log.info("Overpass is at capacity (502): new attempt in {} s", delays.get(attempt));
                sleeper.accept(delays.get(attempt) * 1000L);
            }
        }
    }

    private static String badRequestMessage(String body) {
        Matcher errors = OVERPASS_ERROR.matcher(body == null ? "" : body);
        Set<String> messages = new LinkedHashSet<>();
        while (errors.find()) {
            messages.add(errors.group(1).replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&#39;", "'").replace("&amp;", "&").trim());
        }
        if (messages.isEmpty()) {
            return "Overpass rejected the query (HTTP 400).";
        }
        String message = "Overpass rejected the query (HTTP 400): " + String.join("; ", messages) + ".";
        return message.toLowerCase(Locale.ROOT).contains("regular expression")
                ? message + " Overpass regular expressions are POSIX extended: write groups as (a|b), never (?:a|b)."
                : message;
    }

    private static boolean isBadGateway(RestClientException e) {
        return e instanceof HttpServerErrorException error && error.getStatusCode().value() == 502;
    }

    /** Development only: a file replaces the network call (the same answer for every query, nothing is filtered). */
    private boolean hasFixture() {
        return properties.fixtureFile() != null && !properties.fixtureFile().isBlank();
    }

    private byte[] readFixture() {
        log.warn("Overpass is replaced by the development fixture {}: the data is NOT real", properties.fixtureFile());
        try {
            return Files.readAllBytes(Path.of(properties.fixtureFile()));
        } catch (IOException e) {
            throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "Overpass fixture unreadable", e);
        }
    }

    private static String abbreviate(String text) {
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
