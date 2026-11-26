package hr.emocio.recoresto.search;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.api.SearchRequest;
import hr.emocio.recoresto.search.config.SearchProperties;
import hr.emocio.recoresto.search.criteria.MomentResolver;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import hr.emocio.recoresto.search.criteria.SentenceInterpreter;
import hr.emocio.recoresto.search.google.GooglePlace;
import hr.emocio.recoresto.search.place.PlaceLocator;
import hr.emocio.recoresto.search.restaurant.DeckSelector;
import hr.emocio.recoresto.search.restaurant.NamedPlace;
import hr.emocio.recoresto.search.restaurant.Restaurant;
import hr.emocio.recoresto.search.restaurant.RestaurantFilters;
import hr.emocio.recoresto.search.restaurant.RestaurantSource;
import hr.emocio.recoresto.search.restaurant.SearchAnswer;
import hr.emocio.recoresto.search.restaurant.SearchResult;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a search (spec 008 plan "Architecture"): interpret the sentence, choose the search points (locating
 * the places without position), the radius, ask the source, filter, cut the deck. Logs keep counts, durations and
 * place identifiers only, never business names or addresses (FR-016).
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final SentenceInterpreter interpreter;
    private final PlaceLocator locator;
    private final MomentResolver momentResolver;
    private final RestaurantSource source;
    private final RestaurantFilters filters;
    private final DeckSelector deckSelector;
    private final SearchProperties properties;

    public SearchService(SentenceInterpreter interpreter, PlaceLocator locator, MomentResolver momentResolver,
            RestaurantSource source, RestaurantFilters filters, DeckSelector deckSelector,
            SearchProperties properties) {
        this.interpreter = interpreter;
        this.locator = locator;
        this.momentResolver = momentResolver;
        this.source = source;
        this.filters = filters;
        this.deckSelector = deckSelector;
        this.properties = properties;
    }

    /** A distance of the sentence, else a travel time on foot, else the default radius. */
    private double radiusMeters(SearchCriteria criteria) {
        if (criteria.distanceLimitMeters() != null) {
            return criteria.distanceLimitMeters();
        }
        if (criteria.walkingMinutes() != null) {
            return properties.walkingMeters(criteria.walkingMinutes());
        }
        return properties.defaultRadiusKm() * 1000;
    }

    /**
     * One point per place of the answer: its valid position, else the place located by {@link PlaceLocator} (D13);
     * with no place, the device position (D14).
     */
    private List<Position> searchPoints(SearchAnswer answer, Position device) {
        if (answer.places().isEmpty()) {
            return device == null ? List.of() : List.of(device);
        }
        List<Position> points = new ArrayList<>();
        for (NamedPlace place : answer.places()) {
            points.add(place.position() != null ? place.position() : locate(place));
        }
        return points;
    }

    private Position locate(NamedPlace place) {
        long start = System.nanoTime();
        Position located = locator.locate(place.name());
        log.info("Place located in {} ms", (System.nanoTime() - start) / 1_000_000);
        return located;
    }

    public SearchResult search(SearchRequest request, boolean includeZone) {
        log.info("Search received ({} characters, position {})", request.sentence().length(),
                request.position() == null ? "absent" : "given");
        log.debug("Search sentence: {}", request.sentence());
        SearchAnswer answer = interpreter.interpret(request.sentence());
        log.info("Model answer: {}, places={}", answer.state(), answer.places().size());
        if (answer.state() == SearchAnswer.State.NOT_A_REQUEST) {
            throw new SearchException(SearchErrorCode.NOT_UNDERSTOOD, "The sentence cannot be understood");
        }
        if (answer.state() == SearchAnswer.State.UNREADABLE) {
            throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "The model answer cannot be read");
        }
        if (answer.state() == SearchAnswer.State.OUTSIDE_FRANCE) {
            throw new SearchException(SearchErrorCode.OUTSIDE_FRANCE,
                    "Nappe n'est disponible qu'en France pour l'instant");
        }
        SearchCriteria criteria = answer.criteria();
        // Text Search experiment: the query holds words of the sentence, so only its length is logged at INFO.
        log.info("Search query of {} characters, {} diet(s)", criteria.query() == null ? 0 : criteria.query().length(),
                criteria.diets().size());
        log.debug("Search query: {}", criteria.query());
        ZonedDateTime moment = momentResolver.resolve(criteria.moment());
        List<Position> points = searchPoints(answer, request.position());
        if (points.isEmpty()) {
            throw new SearchException(SearchErrorCode.PLACE_REQUIRED, "A place is required");
        }
        double radiusMeters = radiusMeters(criteria);
        log.info("Moment {}, radius {} m, {} search point(s)", moment, Math.round(radiusMeters), points.size());

        long start = System.nanoTime();
        List<List<GooglePlace>> found = source.find(criteria, points, radiusMeters);
        int foundCount = found.stream().mapToInt(List::size).sum();
        log.info("Source returned {} places in {} ms", foundCount, (System.nanoTime() - start) / 1_000_000);

        List<List<Restaurant>> kept = found.stream()
                .map(places -> filters.apply(places, criteria, radiusMeters, moment)).toList();
        log.info("Filters kept {} of {} places", kept.stream().mapToInt(List::size).sum(), foundCount);
        DeckSelector.Selection selection = deckSelector.select(kept, properties.deckSize());
        log.info("Deck {} restaurants, zone {}; deck place ids {}", selection.deck().size(), selection.zone().size(),
                selection.deck().stream().map(Restaurant::placeId).toList());
        if (selection.deck().isEmpty()) {
            throw new SearchException(SearchErrorCode.NO_RESULT, "No restaurant matches the search");
        }
        return new SearchResult(criteria, selection.deck(), includeZone ? selection.zone() : null);
    }
}
