package hr.emocio.recoresto.search.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.config.GeocoderProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class ApiAdresseClientTest {

    private MockRestServiceServer server;
    private ApiAdresseClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ApiAdresseClient(builder, new GeocoderProperties("https://geo.test"),
                JsonMapper.builder().build());
    }

    @Test
    void resolvesAPlaceNameToCoordinates() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://geo.test/search/")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("q", "Op%C3%A9ra%2C%20Paris")) // the matcher compares the encoded value
                .andExpect(queryParam("limit", "1"))
                .andRespond(withSuccess(
                        "{\"features\":[{\"geometry\":{\"type\":\"Point\",\"coordinates\":[2.3322,48.8708]}}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.geocode("Opéra, Paris")).contains(new Position(48.8708, 2.3322));
        server.verify();
    }

    @Test
    void noResultGivesAnEmptyAnswer() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://geo.test/search/")))
                .andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.geocode("nowhere")).isEmpty();
    }

    @Test
    void serverErrorIsReportedAsServiceUnavailable() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://geo.test/search/")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.geocode("Opéra")).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.SERVICE_UNAVAILABLE));
    }
}
