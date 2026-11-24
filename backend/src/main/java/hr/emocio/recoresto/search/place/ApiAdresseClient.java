package hr.emocio.recoresto.search.place;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.config.GeocoderProperties;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Client of the API Adresse (data.gouv.fr): turns a place name or an address into coordinates. */
@Component
public class ApiAdresseClient {

    private final RestClient client;
    private final JsonMapper mapper;

    public ApiAdresseClient(RestClient.Builder builder, GeocoderProperties properties, JsonMapper mapper) {
        this.client = builder.baseUrl(properties.baseUrl()).build();
        this.mapper = mapper;
    }

    public Optional<Position> geocode(String query) {
        try {
            String response = client.get()
                    .uri(uri -> uri.path("/search/").queryParam("q", "{q}").queryParam("limit", 1).build(query))
                    .retrieve().body(String.class);
            JsonNode coordinates = mapper.readTree(response).path("features").path(0).path("geometry")
                    .path("coordinates");
            if (!coordinates.isArray() || coordinates.size() < 2) {
                return Optional.empty();
            }
            return Optional.of(new Position(coordinates.get(1).asDouble(), coordinates.get(0).asDouble()));
        } catch (RestClientException | JacksonException e) {
            throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "The geocoding service is unavailable",
                    e);
        }
    }
}
