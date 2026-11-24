package hr.emocio.recoresto.search;

import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.api.SearchRequest;
import hr.emocio.recoresto.search.config.SearchProperties;
import hr.emocio.recoresto.search.criteria.CriteriaExtractor;
import hr.emocio.recoresto.search.criteria.MomentResolver;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import hr.emocio.recoresto.search.place.PlaceResolver;
import hr.emocio.recoresto.search.place.ResolvedPlace;
import hr.emocio.recoresto.search.restaurant.DeckSelector;
import hr.emocio.recoresto.search.restaurant.Restaurant;
import hr.emocio.recoresto.search.restaurant.RestaurantFilters;
import hr.emocio.recoresto.search.restaurant.RestaurantSource;
import hr.emocio.recoresto.search.restaurant.SearchResult;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private final CriteriaExtractor extractor;
    private final MomentResolver momentResolver;
    private final PlaceResolver placeResolver;
    private final RestaurantSource source;
    private final RestaurantFilters filters;
    private final DeckSelector deckSelector;
    private final SearchProperties properties;

    public SearchService(CriteriaExtractor extractor, MomentResolver momentResolver, PlaceResolver placeResolver,
            RestaurantSource source, RestaurantFilters filters, DeckSelector deckSelector,
            SearchProperties properties) {
        this.extractor = extractor;
        this.momentResolver = momentResolver;
        this.placeResolver = placeResolver;
        this.source = source;
        this.filters = filters;
        this.deckSelector = deckSelector;
        this.properties = properties;
    }

    public SearchResult search(SearchRequest request, boolean includeZone) {
        SearchCriteria criteria = extractor.extract(request.sentence());
        ZonedDateTime moment = momentResolver.resolve(criteria.moment());
        List<ResolvedPlace> places = placeResolver.resolve(criteria.places());
        if (places.isEmpty()) {
            throw new SearchException(SearchErrorCode.PLACE_REQUIRED, "A place is required");
        }
        double radiusMeters = criteria.distanceLimitMeters() != null ? criteria.distanceLimitMeters()
                : properties.defaultRadiusKm() * 1000;

        List<List<Restaurant>> resultsPerPlace = places.stream()
                .map(place -> filters.apply(source.find(criteria, place, radiusMeters, moment), radiusMeters,
                        moment))
                .toList();
        DeckSelector.Selection selection = deckSelector.select(resultsPerPlace, properties.deckSize());
        return new SearchResult(criteria, selection.deck(), includeZone ? selection.zone() : null);
    }
}
