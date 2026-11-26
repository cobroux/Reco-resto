package hr.emocio.recoresto.search.google;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import hr.emocio.recoresto.search.restaurant.RestaurantSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Restaurants from Google Places Text Search (experiment of 2026-11-26, replacing the Nearby Search of spec 008 D3):
 * one request per search point, whose text is the query of the model ("frites", "restaurant étoilé Michelin"), or
 * {@code restaurant} when the model gave none. The backend builds every request (FR-002). Requests of all search
 * points run in parallel; each point keeps its own list for {@code DeckSelector}.
 */
@Component
public class GooglePlacesRestaurantSource implements RestaurantSource {

    private final GooglePlacesClient client;
    private final GooglePlaceMapper mapper;

    public GooglePlacesRestaurantSource(GooglePlacesClient client, GooglePlaceMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public List<List<GooglePlace>> find(SearchCriteria criteria, List<Position> searchPoints, double radiusMeters) {
        String query = criteria.query() == null ? GooglePlaceTypes.RESTAURANT : criteria.query();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<JsonNode>>> answers = searchPoints.stream()
                    .map(point -> executor.submit(() -> client.searchText(query, point, radiusMeters))).toList();
            List<List<GooglePlace>> perPoint = new ArrayList<>();
            for (Future<List<JsonNode>> answer : answers) {
                perPoint.add(places(result(answer), searchPoints));
            }
            return perPoint;
        }
    }

    /** The places of one point, each once. */
    private List<GooglePlace> places(List<JsonNode> answer, List<Position> searchPoints) {
        Map<Object, GooglePlace> places = new LinkedHashMap<>();
        for (JsonNode node : answer) {
            mapper.map(node, searchPoints).ifPresent(place -> places.putIfAbsent(
                    place.id() != null ? place.id() : new Object(), place));
        }
        return List.copyOf(places.values());
    }

    private static List<JsonNode> result(Future<List<JsonNode>> answer) {
        try {
            return answer.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException failure) {
                throw failure;
            }
            throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "Google Places search failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "Interrupted while searching", e);
        }
    }
}
