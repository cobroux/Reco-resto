package hr.emocio.recoresto.search.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.config.OverpassProperties;
import hr.emocio.recoresto.search.restaurant.OverpassClient.OverpassRefusal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OverpassClientTest {

    private static final String URL = "https://overpass.test/api/interpreter";
    private static final String QUERY = "[out:json][timeout:25];nwr[\"amenity\"=\"restaurant\"](around:100,48.87,2.33);out center;";

    /** The waits asked between two attempts, in milliseconds (nothing really sleeps in the tests). */
    private final List<Long> waits = new ArrayList<>();
    private MockRestServiceServer server;
    private OverpassClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = clientWith(null);
    }

    private OverpassClient clientWith(String fixtureFile) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new OverpassClient(builder.build(),
                new OverpassProperties(URL, 30, 500, 300, 4, fixtureFile, List.of(5, 10, 20)),
                JsonMapper.builder().build(), waits::add);
    }

    @Test
    void fixtureFileReplacesTheNetworkCall(@TempDir Path dir) throws Exception {
        Path fixture = Files.writeString(dir.resolve("fixture.json"), "{\"elements\":[{\"type\":\"node\",\"id\":7}]}");
        OverpassClient offline = clientWith(fixture.toString());

        assertThat(offline.query(QUERY).path("elements").path(0).path("id").asInt()).isEqualTo(7);
        server.verify(); // no request was expected, none was made
    }

    @Test
    void fixtureStillRefusesAQueryWithoutJsonOutput(@TempDir Path dir) throws Exception {
        Path fixture = Files.writeString(dir.resolve("fixture.json"), "{\"elements\":[]}");

        assertThatThrownBy(() -> clientWith(fixture.toString()).query("node(1);out;"))
                .isInstanceOf(OverpassRefusal.class);
    }

    @Test
    void unreadableFixtureIsReportedAsServiceUnavailable(@TempDir Path dir) {
        OverpassClient offline = clientWith(dir.resolve("missing.json").toString());

        assertThatThrownBy(() -> offline.query(QUERY)).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.SERVICE_UNAVAILABLE));
    }

    @Test
    void postsTheQueryAsFormDataAndReturnsTheElements() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(header("User-Agent", OverpassClient.USER_AGENT))
                .andRespond(withSuccess("{\"elements\":[{\"type\":\"node\",\"id\":1,\"lat\":48.87,\"lon\":2.33}]}",
                        MediaType.APPLICATION_JSON));

        JsonNode answer = client.query(QUERY);

        assertThat(answer.path("elements")).hasSize(1);
        server.verify();
    }

    @Test
    void readsAnAnswerSentWithAGenericContentType() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"elements\":[{\"type\":\"node\",\"id\":1,\"tags\":{\"name\":\"Café Opéra\"}}]}",
                MediaType.APPLICATION_OCTET_STREAM));

        assertThat(client.query(QUERY).path("elements").path(0).path("tags").path("name").asString())
                .isEqualTo("Café Opéra");
    }

    @Test
    void anEmptyListOfElementsIsAnAnswerNotAFailure() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"elements\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.query(QUERY).path("elements")).isEmpty();
    }

    @Test
    void emptyOrTooLongQueriesAreRefusedWithoutCallingOverpass() {
        assertThatThrownBy(() -> client.query("  ")).isInstanceOf(OverpassRefusal.class);
        assertThatThrownBy(() -> client.query("[out:json];" + "x".repeat(300))).isInstanceOf(OverpassRefusal.class)
                .hasMessageContaining("300");
        server.verify();
    }

    @Test
    void aQueryWithoutJsonOutputIsRefused() {
        assertThatThrownBy(() -> client.query("[timeout:25];node(1);out;")).isInstanceOf(OverpassRefusal.class)
                .hasMessageContaining("[out:json]");
    }

    @Test
    void anAnswerLargerThanTheLimitIsRefused() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"elements\":[\"" + "x".repeat(600) + "\"]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.query(QUERY)).isInstanceOf(OverpassRefusal.class)
                .hasMessageContaining("too large");
    }

    @Test
    void anAnswerThatIsNotJsonIsRefusedWithAShortExcerpt() {
        server.expect(requestTo(URL)).andRespond(withSuccess("<html>runtime error</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.query(QUERY)).isInstanceOf(OverpassRefusal.class)
                .hasMessageContaining("runtime error");
    }

    @Test
    void serverErrorIsReportedAsServiceUnavailable() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.query(QUERY)).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.SERVICE_UNAVAILABLE));
    }

    private static final String OK = "{\"elements\":[{\"type\":\"node\",\"id\":1}]}";

    @Test
    void aBadGatewayIsRetriedAfterAWaitAndTheLaterAnswerIsUsed() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("# 502 at capacity"));
        server.expect(requestTo(URL)).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        assertThat(client.query(QUERY).path("elements").path(0).path("id").asInt()).isEqualTo(1);

        assertThat(waits).containsExactly(5000L);
        server.verify();
    }

    @Test
    void afterTheLastAttemptABadGatewayIsReportedAsServiceUnavailable() {
        server.expect(ExpectedCount.times(4), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.query(QUERY)).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.SERVICE_UNAVAILABLE));

        assertThat(waits).containsExactly(5000L, 10000L, 20000L);
        server.verify();
    }

    @Test
    void otherServerErrorsAreNotRetried() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.query(QUERY)).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.SERVICE_UNAVAILABLE));

        assertThat(waits).isEmpty();
        server.verify();
    }

    @Test
    void withoutConfiguredDelaysThereIsASingleAttempt() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        OverpassClient noRetry = new OverpassClient(builder.build(),
                new OverpassProperties(URL, 30, 500, 300, 4, null, null), JsonMapper.builder().build(), waits::add);
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> noRetry.query(QUERY)).isInstanceOf(SearchException.class);

        assertThat(waits).isEmpty();
        server.verify();
    }

    private static final String REGEX_ERROR = "<?xml version=\"1.0\"?><html><body><p>The data included in this document "
            + "is from www.openstreetmap.org.</p><p><strong style=\"color:#FF0000\">Error</strong>: line 1: static "
            + "error: Invalid regular expression: &quot;^(?:asian)$&quot; </p></body></html>";

    @Test
    void aBadRequestIsGivenBackAsARefusalWithTheMessageOfOverpassAndIsNotRetried() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST).body(REGEX_ERROR));

        assertThatThrownBy(() -> client.query(QUERY)).isInstanceOf(OverpassRefusal.class)
                .hasMessageContaining("HTTP 400").hasMessageContaining("Invalid regular expression")
                .hasMessageContaining("\"^(?:asian)$\"").hasMessageContaining("(a|b)");

        assertThat(waits).isEmpty();
        server.verify();
    }

    @Test
    void aBadRequestWithAnUnreadableBodyIsStillARefusal() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("nothing useful"));

        assertThatThrownBy(() -> client.query(QUERY)).isInstanceOf(OverpassRefusal.class)
                .hasMessageContaining("HTTP 400");
    }
}
