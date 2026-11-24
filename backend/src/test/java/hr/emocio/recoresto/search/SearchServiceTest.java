package hr.emocio.recoresto.search;

import static hr.emocio.recoresto.search.restaurant.TestRestaurants.open;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hr.emocio.recoresto.search.api.Position;
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
import hr.emocio.recoresto.search.restaurant.TestRestaurants;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SearchServiceTest {

    private final CriteriaExtractor extractor = mock(CriteriaExtractor.class);
    private final PlaceResolver placeResolver = mock(PlaceResolver.class);
    private final RestaurantSource source = mock(RestaurantSource.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneOffset.UTC);
    private final SearchService service = new SearchService(extractor, new MomentResolver(clock), placeResolver,
            source, new RestaurantFilters(), new DeckSelector(), new SearchProperties(2.5, 15));

    private final ResolvedPlace opera = new ResolvedPlace("Opéra", new Position(48.8708, 2.3322));
    private final ResolvedPlace chatelet = new ResolvedPlace("Châtelet", new Position(48.8584, 2.3470));
    private final SearchRequest request = new SearchRequest("asiatique près d'Opéra", null);

    private SearchCriteria criteria(String moment, Integer distanceLimitMeters, String... places) {
        return new SearchCriteria(List.of("asian"), List.of(), null, List.of(places), moment, distanceLimitMeters);
    }

    private static List<Restaurant> restaurants(String prefix, int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> open(prefix + i)).toList();
    }

    @Test
    void returnsTheUnderstoodCriteriaAndTheRestaurants() {
        SearchCriteria criteria = criteria("now", null, "Opéra");
        when(extractor.extract(request.sentence())).thenReturn(criteria);
        when(placeResolver.resolve(List.of("Opéra"))).thenReturn(List.of(opera));
        when(source.find(eq(criteria), eq(opera), eq(2500.0), any())).thenReturn(restaurants("r", 2));

        SearchResult result = service.search(request, false);

        assertThat(result.criteria()).isEqualTo(criteria);
        assertThat(result.deck()).extracting(Restaurant::name).containsExactly("r1", "r2");
        assertThat(result.zone()).isNull();
    }

    @Test
    void usesTheDefaultRadiusWhenTheSentenceHasNoDistance() {
        SearchCriteria criteria = criteria("now", null, "Opéra");
        when(extractor.extract(request.sentence())).thenReturn(criteria);
        when(placeResolver.resolve(List.of("Opéra"))).thenReturn(List.of(opera));
        when(source.find(any(), any(), eq(2500.0), any())).thenReturn(List.of());

        service.search(request, false);

        verify(source).find(any(), eq(opera), eq(2500.0), any());
    }

    @Test
    void aDistanceInTheSentenceReplacesTheDefaultRadius() {
        SearchCriteria criteria = criteria("now", 800, "Opéra");
        when(extractor.extract(request.sentence())).thenReturn(criteria);
        when(placeResolver.resolve(List.of("Opéra"))).thenReturn(List.of(opera));
        when(source.find(any(), any(), eq(800.0), any())).thenReturn(restaurants("near", 1));

        SearchResult result = service.search(request, false);

        verify(source).find(any(), eq(opera), eq(800.0), any());
        assertThat(result.deck()).hasSize(1);
    }

    @Test
    void passesTheTargetedMomentToTheSource() {
        SearchCriteria criteria = criteria("2026-11-28T12:00", null, "Opéra");
        when(extractor.extract(request.sentence())).thenReturn(criteria);
        when(placeResolver.resolve(List.of("Opéra"))).thenReturn(List.of(opera));
        when(source.find(any(), any(), eq(2500.0), any())).thenReturn(List.of());

        service.search(request, false);

        verify(source).find(eq(criteria), eq(opera), eq(2500.0),
                eq(ZonedDateTime.of(2026, 11, 28, 12, 0, 0, 0, ZoneId.of("Europe/Paris"))));
    }

    @Test
    void aMomentInThePastIsNotUnderstoodAndNothingIsSearched() {
        when(extractor.extract(request.sentence())).thenReturn(criteria("2026-11-23T20:00", null, "Opéra"));

        assertThatThrownBy(() -> service.search(request, false)).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.NOT_UNDERSTOOD));
        org.mockito.Mockito.verifyNoInteractions(source);
    }

    @Test
    void deckHasAtMostTheDeckSizeWhileTheZoneKeepsEverything() {
        SearchCriteria criteria = criteria("now", null, "Opéra");
        when(extractor.extract(request.sentence())).thenReturn(criteria);
        when(placeResolver.resolve(List.of("Opéra"))).thenReturn(List.of(opera));
        when(source.find(any(), any(), eq(2500.0), any())).thenReturn(restaurants("r", 20));

        SearchResult result = service.search(request, true);

        assertThat(result.deck()).hasSize(15);
        assertThat(result.zone()).hasSize(20);
    }

    @Test
    void severalPlacesAreSearchedAndMergedWithoutDuplicates() {
        SearchCriteria criteria = criteria("now", null, "Opéra", "Châtelet");
        when(extractor.extract(request.sentence())).thenReturn(criteria);
        when(placeResolver.resolve(List.of("Opéra", "Châtelet"))).thenReturn(List.of(opera, chatelet));
        when(source.find(any(), eq(opera), eq(2500.0), any())).thenReturn(List.of(open("A"), open("B")));
        when(source.find(any(), eq(chatelet), eq(2500.0), any())).thenReturn(List.of(open("B"), open("C")));

        SearchResult result = service.search(request, false);

        assertThat(result.deck()).extracting(Restaurant::name).containsExactly("A", "B", "C");
    }

    @Test
    void restaurantsOutsideTheRadiusOrWithoutAddressAreNotReturned() {
        SearchCriteria criteria = criteria("now", null, "Opéra");
        when(extractor.extract(request.sentence())).thenReturn(criteria);
        when(placeResolver.resolve(List.of("Opéra"))).thenReturn(List.of(opera));
        Restaurant far = TestRestaurants.of("far", "1 rue far", TestRestaurants.CENTER, 3000,
                TestRestaurants.everyDay("00:00", "23:59"));
        Restaurant noAddress = TestRestaurants.of("none", null, TestRestaurants.CENTER, 100,
                TestRestaurants.everyDay("00:00", "23:59"));
        when(source.find(any(), any(), eq(2500.0), any())).thenReturn(List.of(far, noAddress, open("ok")));

        assertThat(service.search(request, false).deck()).extracting(Restaurant::name).containsExactly("ok");
    }

    @Test
    void noResolvablePlaceGivesPlaceRequired() {
        when(extractor.extract(request.sentence())).thenReturn(criteria("now", null));
        when(placeResolver.resolve(List.of())).thenReturn(List.of());

        assertThatThrownBy(() -> service.search(request, false)).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.PLACE_REQUIRED));
    }
}
