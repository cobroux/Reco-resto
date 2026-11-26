package hr.emocio.recoresto.search.google;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.config.GooglePlacesProperties;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP client of the Google Places API (New) (spec 008, contracts/google-places-requests.md): restaurant search
 * (Text Search, experiment of 2026-11-26), place location (Text Search, D13) and photo media (D16 a).
 * <ul>
 * <li>The key is sent in the {@code X-Goog-Api-Key} header only, never in a URL (FR-013); without a key no request is
 * sent.</li>
 * <li>Every failure gives {@code SERVICE_UNAVAILABLE} (FR-012); a server error, a 429 or a timeout gets new attempts
 * after {@code google-places.retry-delays-seconds} (D2).</li>
 * <li>Each failure is logged as a WARN naming the operation and the cause class of the contract, with the HTTP status
 * and the Google status and reason; never the key nor the request headers.</li>
 * <li>In development a fixture file can replace the search (data NOT real).</li>
 * </ul>
 */
@Component
public class GooglePlacesClient {

    /** FR-008: the single list of the Google fields the search uses (address D21 (2), phone D21 (3)). */
    public static final String SEARCH_FIELD_MASK = String.join(",", "places.id", "places.displayName",
            "places.location", "places.shortFormattedAddress", "places.types", "places.primaryType",
            "places.primaryTypeDisplayName", "places.businessStatus", "places.photos", "places.attributions",
            "places.regularOpeningHours", "places.priceLevel", "places.websiteUri", "places.nationalPhoneNumber",
            "places.servesVegetarianFood");

    /** D13: what locating a named place needs (its point and its country). */
    public static final String LOCATE_FIELD_MASK = "places.location,places.addressComponents";

    private static final String API_KEY_HEADER = "X-Goog-Api-Key";
    private static final String FIELD_MASK_HEADER = "X-Goog-FieldMask";

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesClient.class);

    /** Cause classes of contracts/google-places-requests.md "Error mapping" (plus {@code bad-request}). */
    enum Cause {
        KEY_MISSING("key-missing"), KEY_INVALID("key-invalid"), PERMISSION_DENIED("permission-denied"),
        QUOTA("quota"), UNAVAILABLE("unavailable"), TIMEOUT("timeout"), BAD_ANSWER("bad-answer"),
        /** Any other refusal of the request (4xx); not listed in the contract. */
        BAD_REQUEST("bad-request");

        private final String label;

        Cause(String label) {
            this.label = label;
        }
    }

    /** One failed attempt: its cause, whether D2 allows a new attempt, and what may be logged. */
    private record Failure(Cause cause, boolean retryable, String detail) {
    }

    private final RestClient client;
    private final GooglePlacesProperties properties;
    private final JsonMapper mapper;
    private final LongConsumer sleeper;

    GooglePlacesClient(RestClient client, GooglePlacesProperties properties, JsonMapper mapper, LongConsumer sleeper) {
        this.client = client;
        this.properties = properties;
        this.mapper = mapper;
        this.sleeper = sleeper;
        if (!properties.hasApiKey()) {
            log.warn("Google Places API key missing (GOOGLE_PLACES_API_KEY is blank): every search will answer "
                    + "\"service unavailable\"");
        }
    }

    @Autowired
    public GooglePlacesClient(RestClient.Builder builder, GooglePlacesProperties properties, JsonMapper mapper) {
        this(builder.requestFactory(timeouts(properties)).build(), properties, mapper, GooglePlacesClient::sleep);
    }

    private static SimpleClientHttpRequestFactory timeouts(GooglePlacesProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        return factory;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "Interrupted while waiting for Google", e);
        }
    }

    /**
     * One Text Search (experiment of 2026-11-26, replacing the Nearby Search of D3): the restaurants Google finds for
     * the text inside the rectangle holding the circle, one page of at most {@code google-places.max-results}, never
     * the next page (one billed request per search point); an answer without places is an empty list.
     */
    public List<JsonNode> searchText(String query, Position center, double radiusMeters) {
        String operation = "search";
        String body = hasFixture() ? readFixture() : call(operation, () -> post("/places:searchText",
                SEARCH_FIELD_MASK, textRequest(query, center, radiusMeters)));
        List<JsonNode> places = new ArrayList<>();
        readJson(operation, body).path("places").forEach(places::add);
        if (log.isDebugEnabled()) {
            log.debug("Google Places search answered {} places, {} with photos; fields of the first place: {}",
                    places.size(), places.stream().filter(place -> !place.path("photos").isEmpty()).count(),
                    places.isEmpty() ? List.of() : places.getFirst().propertyNames());
        }
        return places;
    }

    /** D13: the first place Google Text Search finds for a place name, empty when none. */
    public Optional<JsonNode> locate(String name) {
        String operation = "locate";
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("textQuery", name);
        request.put("pageSize", 1);
        request.put("languageCode", properties.languageCode());
        request.put("regionCode", properties.regionCode());
        String body = call(operation, () -> post("/places:searchText", LOCATE_FIELD_MASK, request));
        JsonNode first = readJson(operation, body).path("places").path(0);
        return first.isObject() ? Optional.of(first) : Optional.empty();
    }

    /**
     * The image of a photo (D16 a), at the width {@code google-places.photo-max-width-px}; empty when Google does not
     * know the photo (unknown or expired name).
     */
    public Optional<GooglePhoto> photo(String placeId, String photoId) {
        return call("photo", () -> {
            try {
                // exchange, not retrieve: retrieve fails on an unreadable Content-Type before we can see it.
                return client.get()
                        .uri(properties.baseUrl() + "/places/{placeId}/photos/{photoId}/media?maxWidthPx={width}",
                                placeId, photoId, properties.photoMaxWidthPx())
                        .header(API_KEY_HEADER, properties.apiKey()).exchange((request, response) -> {
                            byte[] body = response.getBody().readAllBytes();
                            if (response.getStatusCode().isError()) {
                                throw statusError(response.getStatusCode(), response.getStatusText(),
                                        response.getHeaders(), body);
                            }
                            MediaType type = readableContentType(response.getHeaders());
                            return Optional.of(new GooglePhoto(body,
                                    type == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : type.toString()));
                        });
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            }
        });
    }

    /** The exception {@code retrieve()} would throw for this error status, so that {@link #call} classifies it. */
    private static HttpStatusCodeException statusError(HttpStatusCode status,
            String statusText, HttpHeaders headers, byte[] body) {
        return status.is4xxClientError()
                ? HttpClientErrorException.create(status, statusText, headers, body, null)
                : HttpServerErrorException.create(status, statusText, headers, body, null);
    }

    /** The {@code Content-Type} of an answer, null when absent or unreadable (PR #15 review). */
    private static MediaType readableContentType(HttpHeaders headers) {
        try {
            return headers.getContentType();
        } catch (IllegalArgumentException unreadable) {
            return null;
        }
    }

    /**
     * The Text Search body. {@code includedType} restaurant with {@code strictTypeFiltering}: only places of that type
     * are returned, so that the single page is not spent on bakeries, bars or shops the D6 filter would remove anyway.
     * The location is a restriction (never a bias): Text Search accepts only a rectangle for it.
     */
    private Map<String, Object> textRequest(String query, Position center, double radiusMeters) {
        Position.Box box = center.boundingBox(radiusMeters);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("textQuery", query);
        request.put("includedType", GooglePlaceTypes.RESTAURANT);
        request.put("strictTypeFiltering", true);
        request.put("pageSize", properties.maxResults());
        request.put("locationRestriction", Map.of("rectangle", Map.of("low", point(box.low()), "high",
                point(box.high()))));
        request.put("languageCode", properties.languageCode());
        request.put("regionCode", properties.regionCode());
        return request;
    }

    private static Map<String, Double> point(Position position) {
        return Map.of("latitude", position.latitude(), "longitude", position.longitude());
    }

    private String post(String path, String fieldMask, Map<String, Object> request) {
        String body = client.post().uri(properties.baseUrl() + path).contentType(MediaType.APPLICATION_JSON)
                .header(API_KEY_HEADER, properties.apiKey()).header(FIELD_MASK_HEADER, fieldMask)
                .body(mapper.writeValueAsString(request)).retrieve().body(String.class);
        return body == null ? "" : body;
    }

    /** Runs one Google request with the key check, the new attempts of D2 and the failure logs. */
    private <T> T call(String operation, Supplier<T> request) {
        if (!properties.hasApiKey()) {
            log.warn("Google Places {} failed: {} (no request sent)", operation, Cause.KEY_MISSING.label);
            throw unavailable(null);
        }
        List<Integer> delays = properties.retryDelaysSeconds();
        for (int attempt = 0;; attempt++) {
            try {
                return request.get();
            } catch (RestClientException e) {
                Failure failure = classify(e);
                boolean retry = failure.retryable() && attempt < delays.size();
                log.warn("Google Places {} failed: {} ({}), attempt {} of {}{}", operation, failure.cause().label,
                        failure.detail(), attempt + 1, delays.size() + 1,
                        retry ? "; new attempt in " + delays.get(attempt) + " s" : "");
                if (!retry) {
                    throw unavailable(e);
                }
                sleeper.accept(delays.get(attempt) * 1000L);
            }
        }
    }

    /** The cause class of a failed request; only the status, the Google status and reason are kept for the log. */
    private Failure classify(RestClientException e) {
        if (e instanceof HttpStatusCodeException http) {
            int status = http.getStatusCode().value();
            String detail = "HTTP " + status + googleError(http.getResponseBodyAsString());
            if (status == 400 && detail.contains("API_KEY_INVALID")) {
                return new Failure(Cause.KEY_INVALID, false, detail);
            }
            if (status == 403) {
                return new Failure(Cause.PERMISSION_DENIED, false, detail);
            }
            if (status == 429) {
                return new Failure(Cause.QUOTA, true, detail);
            }
            if (status >= 500) {
                return new Failure(Cause.UNAVAILABLE, true, detail);
            }
            return new Failure(Cause.BAD_REQUEST, false, detail);
        }
        if (e instanceof ResourceAccessException) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                    return new Failure(Cause.TIMEOUT, true, cause.getClass().getSimpleName());
                }
            }
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return new Failure(Cause.UNAVAILABLE, false, cause.getClass().getSimpleName());
        }
        return new Failure(Cause.UNAVAILABLE, false, e.getClass().getSimpleName());
    }

    /** ", status RESOURCE_EXHAUSTED, reason API_KEY_INVALID" from a Google error body; empty when there is none. */
    private String googleError(String body) {
        try {
            JsonNode error = mapper.readTree(body == null ? "" : body).path("error");
            StringBuilder detail = new StringBuilder();
            if (error.path("status").isString()) {
                detail.append(", status ").append(error.path("status").asString());
            }
            for (JsonNode item : error.path("details")) {
                if (item.path("reason").isString()) {
                    detail.append(", reason ").append(item.path("reason").asString());
                }
            }
            return detail.toString();
        } catch (JacksonException notJson) {
            return "";
        }
    }

    private JsonNode readJson(String operation, String body) {
        try {
            return mapper.readTree(body);
        } catch (JacksonException e) {
            log.warn("Google Places {} failed: {} (unreadable JSON)", operation, Cause.BAD_ANSWER.label);
            throw unavailable(e);
        }
    }

    private static SearchException unavailable(Throwable cause) {
        return new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "Google Places is unavailable", cause);
    }

    /** Development only: a file replaces Google (the same answer for every search). */
    private boolean hasFixture() {
        return properties.fixtureFile() != null && !properties.fixtureFile().isBlank();
    }

    private String readFixture() {
        log.warn("Google Places is replaced by the development fixture {}: the data is NOT real",
                properties.fixtureFile());
        try {
            return Files.readString(Path.of(properties.fixtureFile()));
        } catch (IOException e) {
            throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "Google Places fixture unreadable", e);
        }
    }
}
