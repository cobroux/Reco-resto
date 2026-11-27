package hr.emocio.recoresto.search.enrichment;

import hr.emocio.recoresto.search.config.CardEnrichmentProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP client of the Unsplash photo search (spec 007 amendment (b), FR-013, FR-014; research R14).
 * <ul>
 * <li>{@code GET <base-url>/search/photos?query=...&per_page=<per-page>&lang=fr}; the key is sent in the
 * {@code Authorization: Client-ID} header only, never in a URL (FR-014, T39); without a key no request is sent.</li>
 * <li>A fixed trusted API host from configuration: not routed through {@link SafeWebFetcher}. The returned image and
 * profile addresses reach the browser, so only absolute {@code https} ones are kept.</li>
 * <li>The request gets the remaining budget of the enrichment as timeout (FR-011), like {@link SafeWebFetcher}.</li>
 * <li>Each failure (429, other status, unreachable, unreadable answer) is one WARN line with the status or the cause
 * class only, never the key, the headers nor the query (T34, T39), as {@code GooglePlacesClient} logs.</li>
 * </ul>
 */
@Component
public class UnsplashClient {

    /** Spec FR-013: the search is in French. */
    static final String LANG = "fr";

    private static final String API_VERSION = "v1";

    private static final Logger log = LoggerFactory.getLogger(UnsplashClient.class);

    private final RestClient.Builder builder;
    private final HttpClient httpClient;
    private final CardEnrichmentProperties.Unsplash properties;
    private final JsonMapper mapper;

    public UnsplashClient(RestClient.Builder builder, CardEnrichmentProperties properties, JsonMapper mapper) {
        this.builder = builder;
        this.properties = properties.unsplash();
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(properties.budgetSeconds()))
                .build();
        if (!isEnabled()) {
            log.warn("Unsplash access key missing (UNSPLASH_ACCESS_KEY is blank): cards get no cuisine photo");
        }
    }

    /** Whether a key is configured (T36). */
    public boolean isEnabled() {
        return properties.hasAccessKey();
    }

    /**
     * The usable photos of the first {@code per-page} results, in answer order; empty when Unsplash finds none.
     *
     * @throws FetchException {@code UNSPLASH_UNAVAILABLE} on any failure or without key, {@code TIMEOUT} when the
     *         budget is spent
     */
    public List<UnsplashPhoto> search(String query, Deadline deadline) throws FetchException {
        if (!isEnabled()) {
            throw new FetchException(EnrichmentOutcome.UNSPLASH_UNAVAILABLE, "No Unsplash key");
        }
        Duration remaining = deadline.remaining();
        if (remaining.compareTo(Duration.ZERO) <= 0) {
            throw new FetchException(EnrichmentOutcome.TIMEOUT, "Budget spent");
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(remaining);
        String body;
        try {
            body = builder.clone().requestFactory(factory).build().get()
                    .uri(properties.baseUrl() + "/search/photos?query={query}&per_page={perPage}&lang={lang}", query,
                            properties.perPage(), LANG)
                    .header(HttpHeaders.AUTHORIZATION, "Client-ID " + properties.accessKey())
                    .header("Accept-Version", API_VERSION)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(String.class);
        } catch (HttpStatusCodeException e) {
            throw failure("HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            if (deadline.isSpent() || Thread.currentThread().isInterrupted()) {
                throw new FetchException(EnrichmentOutcome.TIMEOUT, "Budget spent during the Unsplash request");
            }
            throw failure("unreachable (" + SafeWebFetcher.rootCause(e) + ")");
        }
        return photos(body);
    }

    private List<UnsplashPhoto> photos(String body) throws FetchException {
        JsonNode results;
        try {
            results = mapper.readTree(body == null ? "" : body).path("results");
        } catch (JacksonException e) {
            throw failure("unreadable answer");
        }
        if (!results.isArray()) {
            throw failure("unreadable answer");
        }
        List<UnsplashPhoto> photos = new ArrayList<>();
        int read = 0;
        for (JsonNode result : results) {
            if (read++ >= properties.perPage()) {
                break;
            }
            String image = text(result.path("urls").path("regular"));
            String name = text(result.path("user").path("name"));
            String profile = text(result.path("user").path("links").path("html"));
            if (isHttps(image) && isHttps(profile) && name != null && !name.isBlank()) {
                photos.add(new UnsplashPhoto(image, name.trim(), profile));
            }
        }
        return photos;
    }

    private static String text(JsonNode node) {
        return node.isString() ? node.asString() : null;
    }

    /** An absolute https address with a host: nothing else reaches the browser (research R14). */
    private static boolean isHttps(String address) {
        if (address == null) {
            return false;
        }
        try {
            URI uri = new URI(address);
            return uri.getScheme() != null && uri.getScheme().toLowerCase(Locale.ROOT).equals("https")
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static FetchException failure(String detail) {
        log.warn("Unsplash search failed: {}", detail);
        return new FetchException(EnrichmentOutcome.UNSPLASH_UNAVAILABLE, detail);
    }
}
