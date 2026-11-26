package hr.emocio.recoresto.search.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.config.GooglePlacesProperties;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Spec 008 T1, T38, T39; restaurant search by Text Search (experiment of 2026-11-26, replacing D3 Nearby Search). */
class GooglePlacesClientTest {

    private static final String BASE = "https://places.test/v1";
    private static final String KEY = "test-key-123";
    private static final Position OPERA = new Position(48.8708, 2.3322);

    private MockRestServiceServer server;

    /** The waits asked between two attempts, in milliseconds (nothing really sleeps in the tests). */
    private final List<Long> waits = new ArrayList<>();

    private GooglePlacesClient client(String fixtureFile) {
        return client(KEY, fixtureFile);
    }

    private GooglePlacesClient client(String key, String fixtureFile) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new GooglePlacesClient(builder.build(), new GooglePlacesProperties(BASE, key, 10, List.of(1), 20, 800,
                "fr", "FR", fixtureFile), JsonMapper.builder().build(), waits::add);
    }

    /**
     * Text Search experiment (2026-11-26): one request per point, the text of the model, only restaurants, the
     * rectangle holding the search circle (Text Search restricts to a rectangle only), one page of max-results.
     */
    @Test
    void searchTextSendsTheBuiltRequestWithTheKeyInAHeaderOnly() {
        GooglePlacesClient client = client(null);
        server.expect(requestTo(BASE + "/places:searchText"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", KEY))
                .andExpect(header("X-Goog-FieldMask", GooglePlacesClient.SEARCH_FIELD_MASK))
                .andExpect(requestTo(Matchers.not(Matchers.containsString(KEY))))
                .andExpect(jsonPath("$.textQuery").value("frites"))
                .andExpect(jsonPath("$.includedType").value("restaurant"))
                .andExpect(jsonPath("$.strictTypeFiltering").value(true))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.locationRestriction.rectangle.low.latitude").value(Matchers.closeTo(48.848317, 1e-6)))
                .andExpect(jsonPath("$.locationRestriction.rectangle.low.longitude").value(Matchers.closeTo(2.298019, 1e-6)))
                .andExpect(jsonPath("$.locationRestriction.rectangle.high.latitude").value(Matchers.closeTo(48.893283, 1e-6)))
                .andExpect(jsonPath("$.locationRestriction.rectangle.high.longitude").value(Matchers.closeTo(2.366381, 1e-6)))
                .andExpect(jsonPath("$.locationRestriction.circle").doesNotExist())
                .andExpect(jsonPath("$.includedTypes").doesNotExist())
                .andExpect(jsonPath("$.maxResultCount").doesNotExist())
                .andExpect(jsonPath("$.pageToken").doesNotExist())
                .andExpect(jsonPath("$.languageCode").value("fr"))
                .andExpect(jsonPath("$.regionCode").value("FR"))
                .andRespond(withSuccess("{\"places\":[{\"id\":\"a\"},{\"id\":\"b\"}],\"nextPageToken\":\"t\"}",
                        MediaType.APPLICATION_JSON));

        List<JsonNode> places = client.searchText("frites", OPERA, 2500);

        assertThat(places).extracting(place -> place.path("id").asString()).containsExactly("a", "b");
        server.verify(); // one request only: the next page is never asked for
    }

    /** FR-008, D21 (2) and (3): the single list of the Google fields used. */
    @Test
    void theFieldMaskIsTheContractList() {
        assertThat(GooglePlacesClient.SEARCH_FIELD_MASK).isEqualTo("places.id,places.displayName,places.location,"
                + "places.shortFormattedAddress,places.types,places.primaryType,places.primaryTypeDisplayName,"
                + "places.businessStatus,places.photos,places.attributions,places.regularOpeningHours,"
                + "places.priceLevel,places.websiteUri,places.nationalPhoneNumber,places.servesVegetarianFood");
    }

    /** T38: Google found no place. */
    @Test
    void anAnswerWithoutPlacesIsAnEmptyList() {
        GooglePlacesClient client = client(null);
        server.expect(requestTo(BASE + "/places:searchText"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.searchText("restaurant", OPERA, 2500)).isEmpty();
    }

    @Test
    void aClientErrorIsServiceUnavailable() {
        GooglePlacesClient client = client(null);
        server.expect(requestTo(BASE + "/places:searchText")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertUnavailable(() -> client.searchText("restaurant", OPERA, 2500));
    }

    @Test
    void anUnreadableAnswerIsServiceUnavailable() {
        GooglePlacesClient client = client(null);
        server.expect(requestTo(BASE + "/places:searchText"))
                .andRespond(withSuccess("{not json", MediaType.APPLICATION_JSON));

        assertUnavailable(() -> client.searchText("restaurant", OPERA, 2500));
    }

    @Test
    void theFixtureFileReplacesTheRequest(@TempDir Path dir) throws Exception {
        Path fixture = Files.writeString(dir.resolve("fixture.json"), "{\"places\":[{\"id\":\"fixture-1\"}]}");
        GooglePlacesClient client = client(fixture.toString());

        assertThat(client.searchText("restaurant", OPERA, 2500))
                .extracting(place -> place.path("id").asString()).containsExactly("fixture-1");
        server.verify(); // no request was expected, none was made
    }

    @Test
    void theDevelopmentFixtureOfTheRepositoryIsReadable() {
        GooglePlacesClient client = client("dev/google-places-fixture.json");

        assertThat(client.searchText("restaurant", OPERA, 2500)).isNotEmpty();
    }

    @Test
    void anUnreadableFixtureIsServiceUnavailable(@TempDir Path dir) {
        GooglePlacesClient client = client(dir.resolve("missing.json").toString());

        assertUnavailable(() -> client.searchText("restaurant", OPERA, 2500));
    }

    private static void assertUnavailable(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.SERVICE_UNAVAILABLE));
    }

    // --- Photo media (T036: T29, T30, T39; D16) ---

    @Test
    void aPhotoIsFetchedWithTheKeyInAHeaderAndReturnedWithItsContentType() {
        GooglePlacesClient client = client(null);
        byte[] image = {1, 2, 3};
        server.expect(requestTo(BASE + "/places/p1/photos/ph_1-x/media?maxWidthPx=800"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Goog-Api-Key", KEY))
                .andRespond(withSuccess(image, MediaType.IMAGE_JPEG));

        GooglePhoto photo = client.photo("p1", "ph_1-x").orElseThrow();

        assertThat(photo.bytes()).containsExactly(1, 2, 3);
        assertThat(photo.contentType()).isEqualTo("image/jpeg");
        server.verify();
    }

    /** PR #15 review: an unreadable type from Google is no failure; the controller refuses to serve it. */
    @Test
    void anUnreadablePhotoContentTypeIsKeptAsOctetStream() {
        GooglePlacesClient client = client(null);
        server.expect(requestTo(BASE + "/places/p1/photos/ph1/media?maxWidthPx=800"))
                .andRespond(withSuccess(new byte[] {1}, null).header("Content-Type", "not a type"));

        assertThat(client.photo("p1", "ph1").orElseThrow().contentType())
                .isEqualTo(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    @Test
    void anUnknownOrExpiredPhotoIsNotFound() {
        GooglePlacesClient client = client(null);
        server.expect(requestTo(BASE + "/places/p1/photos/old/media?maxWidthPx=800"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.photo("p1", "old")).isEmpty();
    }

    @Test
    void anyOtherPhotoFailureIsServiceUnavailable() {
        GooglePlacesClient client = client(null);
        server.expect(requestTo(BASE + "/places/p1/photos/x/media?maxWidthPx=800"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertUnavailable(() -> client.photo("p1", "x"));
    }

    // --- Place location (T044, D13): Text Search ---

    @Test
    void locateSendsATextSearchForTheFirstPlaceWithTheLocationMask() {
        GooglePlacesClient client = client(null);
        server.expect(requestTo(BASE + "/places:searchText"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", KEY))
                .andExpect(header("X-Goog-FieldMask", "places.location,places.addressComponents"))
                .andExpect(jsonPath("$.textQuery").value("Opéra, Paris"))
                .andExpect(jsonPath("$.pageSize").value(1))
                .andExpect(jsonPath("$.languageCode").value("fr"))
                .andExpect(jsonPath("$.regionCode").value("FR"))
                .andRespond(withSuccess("{\"places\":[{\"location\":{\"latitude\":48.87,\"longitude\":2.33}}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.locate("Opéra, Paris")).hasValueSatisfying(place -> assertThat(
                place.path("location").path("latitude").asDouble()).isEqualTo(48.87));
        server.verify();
    }

    @Test
    void locateWithoutPlaceIsEmpty() {
        GooglePlacesClient client = client(null);
        server.expect(requestTo(BASE + "/places:searchText")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.locate("Nulle part")).isEmpty();
    }

    // --- Failures (T051: T33-T37, T39; D2; contracts/google-places-requests.md "Error mapping") ---

    private static final String SEARCH = BASE + "/places:searchText";

    private List<String> warnings(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(GooglePlacesClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list.stream().filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** Runs a search that must fail with SERVICE_UNAVAILABLE and returns the warnings it logged. */
    private List<String> failingSearch(GooglePlacesClient client) {
        List<String> messages = warnings(() -> assertUnavailable(() -> client.searchText("restaurant",
                OPERA, 2500)));
        assertThat(messages).isNotEmpty().allSatisfy(m -> assertThat(m).doesNotContain(KEY)
                .doesNotContain("X-Goog").contains("search"));
        return messages;
    }

    /** T33: a server error is retried once after 1 s (D2), then unavailable. */
    @Test
    void aServerErrorIsRetriedOnceAfterOneSecond() {
        GooglePlacesClient client = client(null);
        server.expect(ExpectedCount.times(2), requestTo(SEARCH)).andRespond(withServerError());

        assertThat(failingSearch(client)).anySatisfy(m -> assertThat(m).contains("unavailable").contains("500"));
        assertThat(waits).containsExactly(1000L);
        server.verify();
    }

    @Test
    void aRetryThatSucceedsGivesTheAnswer() {
        GooglePlacesClient client = client(null);
        server.expect(requestTo(SEARCH)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(SEARCH)).andRespond(withSuccess("{\"places\":[{\"id\":\"a\"}]}",
                MediaType.APPLICATION_JSON));

        assertThat(client.searchText("restaurant", OPERA, 2500)).hasSize(1);
        server.verify();
    }

    /** T33: a connection error is not retried (D2: server error, 429 and timeout only). */
    @Test
    void aConnectionErrorIsUnavailableWithoutRetry() {
        GooglePlacesClient client = client(null);
        server.expect(ExpectedCount.once(), requestTo(SEARCH)).andRespond(withException(new ConnectException(
                "Connection refused")));

        assertThat(failingSearch(client)).anySatisfy(m -> assertThat(m).contains("unavailable"));
        assertThat(waits).isEmpty();
        server.verify();
    }

    /** T34. */
    @Test
    void aTimeoutIsRetriedOnceThenUnavailable() {
        GooglePlacesClient client = client(null);
        server.expect(ExpectedCount.times(2), requestTo(SEARCH)).andRespond(withException(
                new SocketTimeoutException("Read timed out")));

        assertThat(failingSearch(client)).anySatisfy(m -> assertThat(m).contains("timeout"));
        assertThat(waits).containsExactly(1000L);
        server.verify();
    }

    /** T35. */
    @Test
    void aQuotaRefusalIsRetriedOnceThenUnavailable() {
        GooglePlacesClient client = client(null);
        server.expect(ExpectedCount.times(2), requestTo(SEARCH)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}"));

        assertThat(failingSearch(client)).anySatisfy(m -> assertThat(m).contains("quota")
                .contains("RESOURCE_EXHAUSTED"));
        server.verify();
    }

    /** T36: no key, no request. */
    @Test
    void aBlankKeySendsNoRequest() {
        GooglePlacesClient client = client(" ", null);

        assertThat(failingSearch(client)).anySatisfy(m -> assertThat(m).contains("key-missing"));
        assertUnavailable(() -> client.locate("Opéra"));
        assertUnavailable(() -> client.photo("p1", "ph1"));
        server.verify(); // no request was expected, none was made
    }

    /** T37: the key value is never logged. */
    @Test
    void anInvalidKeyIsReportedWithoutItsValueAndNotRetried() {
        GooglePlacesClient client = client(null);
        server.expect(ExpectedCount.once(), requestTo(SEARCH)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass a valid API key.\","
                        + "\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"@type\":"
                        + "\"type.googleapis.com/google.rpc.ErrorInfo\",\"reason\":\"API_KEY_INVALID\"}]}}"));

        assertThat(failingSearch(client)).anySatisfy(m -> assertThat(m).contains("key-invalid")
                .contains("API_KEY_INVALID"));
        assertThat(waits).isEmpty();
        server.verify();
    }

    @Test
    void aPermissionRefusalIsNotRetried() {
        GooglePlacesClient client = client(null);
        server.expect(ExpectedCount.once(), requestTo(SEARCH)).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":403,\"status\":\"PERMISSION_DENIED\"}}"));

        assertThat(failingSearch(client)).anySatisfy(m -> assertThat(m).contains("permission-denied"));
        server.verify();
    }

    @Test
    void anUnreadableAnswerIsABadAnswerNotRetried() {
        GooglePlacesClient client = client(null);
        server.expect(ExpectedCount.once(), requestTo(SEARCH)).andRespond(withSuccess("<html>", MediaType.TEXT_HTML));

        assertThat(failingSearch(client)).anySatisfy(m -> assertThat(m).contains("bad-answer"));
        server.verify();
    }

    /** D2 applies to every operation. */
    @Test
    void locateAndPhotoAreRetriedToo() {
        GooglePlacesClient client = client(null);
        server.expect(requestTo(BASE + "/places:searchText")).andRespond(withServerError());
        server.expect(requestTo(BASE + "/places:searchText")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/places/p1/photos/ph1/media?maxWidthPx=800"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(BASE + "/places/p1/photos/ph1/media?maxWidthPx=800"))
                .andRespond(withSuccess(new byte[] {7}, MediaType.IMAGE_PNG));

        assertThat(client.locate("Opéra")).isEmpty();
        assertThat(client.photo("p1", "ph1")).isPresent();
        assertThat(waits).containsExactly(1000L, 1000L);
        server.verify();
    }

    @Test
    void theWarningsNameTheOperation() {
        GooglePlacesClient client = client(null);
        server.expect(ExpectedCount.once(), requestTo(BASE + "/places:searchText"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThat(warnings(() -> assertUnavailable(() -> client.locate("Opéra"))))
                .anySatisfy(m -> assertThat(m).contains("locate").contains("permission-denied"));
    }

    // --- Startup (T052: T36) ---

    @Test
    void aBlankKeyLogsOneWarningAtStartup() {
        assertThat(warnings(() -> client("", null))).singleElement()
                .satisfies(m -> assertThat(m).contains("GOOGLE_PLACES_API_KEY"));
        assertThat(warnings(() -> client(null, null))).hasSize(1);
        assertThat(warnings(() -> client(KEY, null))).isEmpty();
    }
}
