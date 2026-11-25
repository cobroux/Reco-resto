package hr.emocio.recoresto.search;

import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.api.SearchRequest;
import hr.emocio.recoresto.search.config.SearchProperties;
import hr.emocio.recoresto.search.criteria.MomentResolver;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import hr.emocio.recoresto.search.restaurant.DeckSelector;
import hr.emocio.recoresto.search.restaurant.Restaurant;
import hr.emocio.recoresto.search.restaurant.RestaurantFilters;
import hr.emocio.recoresto.search.restaurant.RestaurantSource;
import hr.emocio.recoresto.search.restaurant.SearchAnswer;
import hr.emocio.recoresto.search.restaurant.SearchResult;
import java.time.ZonedDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final MomentResolver momentResolver;
    private final RestaurantSource source;
    private final RestaurantFilters filters;
    private final DeckSelector deckSelector;
    private final SearchProperties properties;

    public SearchService(MomentResolver momentResolver, RestaurantSource source, RestaurantFilters filters,
            DeckSelector deckSelector, SearchProperties properties) {
        this.momentResolver = momentResolver;
        this.source = source;
        this.filters = filters;
        this.deckSelector = deckSelector;
        this.properties = properties;
    }

    public SearchResult search(SearchRequest request, boolean includeZone) {
        log.info("Search received ({} characters, position {})", request.sentence().length(),
                request.position() == null ? "absent" : "given");
        log.debug("Search sentence: {}", request.sentence());
        SearchAnswer answer = source.find(request.sentence());
        log.info("Source answer: understood={}, outsideFrance={}, centers={}, restaurants={}", answer.understood(),
                answer.outsideFrance(), answer.centers().size(), answer.restaurants().size());
        if (!answer.understood()) {
            throw new SearchException(SearchErrorCode.NOT_UNDERSTOOD, "The sentence cannot be understood");
        }
        if (answer.outsideFrance()) {
            throw new SearchException(SearchErrorCode.OUTSIDE_FRANCE,
                    "Nappe n'est disponible qu'en France pour l'instant");
        }
        SearchCriteria criteria = answer.criteria();
        ZonedDateTime moment = momentResolver.resolve(criteria.moment());
        if (answer.centers().isEmpty()) {
            throw new SearchException(SearchErrorCode.PLACE_REQUIRED, "A place is required");
        }
        double radiusMeters = criteria.distanceLimitMeters() != null ? criteria.distanceLimitMeters()
                : properties.defaultRadiusKm() * 1000;

        log.info("Moment {}, radius {} m", moment, Math.round(radiusMeters));
        // The answer holds the restaurants of every place; their distance is the one to the nearest place.
        List<Restaurant> kept = filters.apply(answer.restaurants(), radiusMeters, moment);
        log.info("Filters kept {} of {} restaurants", kept.size(), answer.restaurants().size());
        DeckSelector.Selection selection = deckSelector.select(List.of(kept), properties.deckSize());
        log.info("Deck {} restaurants, zone {}", selection.deck().size(), selection.zone().size());
        if (selection.deck().isEmpty()) {
            throw new SearchException(SearchErrorCode.NO_RESULT, "No restaurant matches the search");
        }
        return new SearchResult(criteria, selection.deck(), includeZone ? selection.zone() : null);
    }
}
