package hr.emocio.recoresto.search.google;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.criteria.CriteriaMatcher;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import hr.emocio.recoresto.search.restaurant.RestaurantSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Restaurants from Google Places Nearby Search (spec 008, D3): the backend builds every request from the criteria
 * (FR-002). Requests of all search points run in parallel; each point keeps its own list for {@code DeckSelector}.
 * <ul>
 * <li>No cuisine constraint (no cuisine, or an OR branch holding a diet): only {@code restaurant} is asked for, the D6
 * rule is applied by the backend filters (decision Q1).</li>
 * <li>Cuisines: their Google types, at most {@link #MAX_INCLUDED_TYPES} per request (research R1); more are split into
 * several requests (decision Q1).</li>
 * </ul>
 */
@Component
public class GooglePlacesRestaurantSource implements RestaurantSource {

    /** Google limit on {@code includedTypes} (research R1: "Up to 50 types can be specified"). */
    static final int MAX_INCLUDED_TYPES = 50;

    private final GooglePlacesClient client;
    private final GooglePlaceMapper mapper;

    public GooglePlacesRestaurantSource(GooglePlacesClient client, GooglePlaceMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public List<List<GooglePlace>> find(SearchCriteria criteria, List<Position> searchPoints, double radiusMeters) {
        List<List<String>> requests = includedTypes(criteria);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<List<Future<List<JsonNode>>>> answers = new ArrayList<>();
            for (Position point : searchPoints) {
                answers.add(requests.stream().map(types -> executor.submit(() -> client.searchNearby(types, point,
                        radiusMeters))).toList());
            }
            List<List<GooglePlace>> perPoint = new ArrayList<>();
            for (List<Future<List<JsonNode>>> pointAnswers : answers) {
                perPoint.add(places(pointAnswers, searchPoints));
            }
            return perPoint;
        }
    }

    /** The places of one point, each once (the same place can be in the answers of two requests of a point). */
    private List<GooglePlace> places(List<Future<List<JsonNode>>> answers, List<Position> searchPoints) {
        Map<Object, GooglePlace> places = new LinkedHashMap<>();
        for (Future<List<JsonNode>> answer : answers) {
            for (JsonNode node : result(answer)) {
                mapper.map(node, searchPoints).ifPresent(place -> places.putIfAbsent(
                        place.id() != null ? place.id() : new Object(), place));
            }
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

    /** The {@code includedTypes} of each request for one search point (decision Q1). */
    static List<List<String>> includedTypes(SearchCriteria criteria) {
        Set<String> cuisines = CriteriaMatcher.requiredCuisineTypes(criteria).orElse(Set.of());
        if (cuisines.isEmpty()) {
            return List.of(List.of(GooglePlaceTypes.RESTAURANT));
        }
        List<String> all = List.copyOf(cuisines);
        List<List<String>> requests = new ArrayList<>();
        for (int start = 0; start < all.size(); start += MAX_INCLUDED_TYPES) {
            requests.add(all.subList(start, Math.min(all.size(), start + MAX_INCLUDED_TYPES)));
        }
        return requests;
    }
}
