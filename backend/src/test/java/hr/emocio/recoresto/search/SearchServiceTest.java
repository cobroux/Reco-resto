package hr.emocio.recoresto.search;

import static hr.emocio.recoresto.search.restaurant.TestRestaurants.open;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import hr.emocio.recoresto.search.api.Position;
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
import hr.emocio.recoresto.search.restaurant.TestRestaurants;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SearchServiceTest {

    private final RestaurantSource source = mock(RestaurantSource.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneOffset.UTC);
    private final SearchService service = new SearchService(new MomentResolver(clock), source,
            new RestaurantFilters(), new DeckSelector(), new SearchProperties(2.5, 15, 5));

    private final Position opera = new Position(48.8708, 2.3322);
    private final SearchRequest request = new SearchRequest("asiatique près d'Opéra", null);

    private SearchCriteria criteria(String moment, Integer distanceLimitMeters) {
        return new SearchCriteria(List.of("asian"), List.of(), null, List.of("Opéra"), moment, distanceLimitMeters,
                null);
    }

    private SearchCriteria criteriaWalking(Integer walkingMinutes, Integer distanceLimitMeters) {
        return new SearchCriteria(List.of("asian"), List.of(), null, List.of("Opéra"), "now", distanceLimitMeters,
                walkingMinutes);
    }

    private SearchAnswer answer(SearchCriteria criteria, List<Position> centers, List<Restaurant> restaurants) {
        return new SearchAnswer(true, false, criteria, centers, restaurants);
    }

    private void sourceAnswers(SearchAnswer answer) {
        when(source.find(request.sentence())).thenReturn(answer);
    }

    private static List<Restaurant> restaurants(String prefix, int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> open(prefix + i)).toList();
    }

    @Test
    void returnsTheUnderstoodCriteriaAndTheRestaurants() {
        SearchCriteria criteria = criteria("now", null);
        sourceAnswers(answer(criteria, List.of(opera), restaurants("r", 2)));

        SearchResult result = service.search(request, false);

        assertThat(result.criteria()).isEqualTo(criteria);
        assertThat(result.deck()).extracting(Restaurant::name).containsExactly("r1", "r2");
        assertThat(result.zone()).isNull();
    }

    @Test
    void usesTheDefaultRadiusWhenTheSentenceHasNoDistance() {
        Restaurant atTwoKm = TestRestaurants.of("two", "1 rue two", TestRestaurants.CENTER, 2400,
                TestRestaurants.everyDay("00:00", "23:59"));
        Restaurant tooFar = TestRestaurants.of("far", "1 rue far", TestRestaurants.CENTER, 2600,
                TestRestaurants.everyDay("00:00", "23:59"));
        sourceAnswers(answer(criteria("now", null), List.of(opera), List.of(atTwoKm, tooFar)));

        assertThat(service.search(request, false).deck()).extracting(Restaurant::name).containsExactly("two");
    }

    @Test
    void aDistanceInTheSentenceReplacesTheDefaultRadius() {
        Restaurant near = TestRestaurants.of("near", "1 rue near", TestRestaurants.CENTER, 700,
                TestRestaurants.everyDay("00:00", "23:59"));
        Restaurant farther = TestRestaurants.of("farther", "1 rue farther", TestRestaurants.CENTER, 900,
                TestRestaurants.everyDay("00:00", "23:59"));
        sourceAnswers(answer(criteria("now", 800), List.of(opera), List.of(near, farther)));

        assertThat(service.search(request, false).deck()).extracting(Restaurant::name).containsExactly("near");
    }

    @Test
    void aTravelTimeInTheSentenceIsAWalkingDistanceAt5KmPerHour() {
        Restaurant at800 = TestRestaurants.of("near", "1 rue near", TestRestaurants.CENTER, 800,
                TestRestaurants.everyDay("00:00", "23:59"));
        Restaurant at900 = TestRestaurants.of("farther", "1 rue farther", TestRestaurants.CENTER, 900,
                TestRestaurants.everyDay("00:00", "23:59"));
        sourceAnswers(answer(criteriaWalking(10, null), List.of(opera), List.of(at800, at900)));

        // 10 minutes on foot at 5 km/h = 833 m
        assertThat(service.search(request, false).deck()).extracting(Restaurant::name).containsExactly("near");
    }

    @Test
    void aDistanceInMetersWinsOverATravelTime() {
        Restaurant at800 = TestRestaurants.of("near", "1 rue near", TestRestaurants.CENTER, 800,
                TestRestaurants.everyDay("00:00", "23:59"));
        sourceAnswers(answer(criteriaWalking(30, 500), List.of(opera), List.of(at800)));

        assertThatThrownBy(() -> service.search(request, false)).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.NO_RESULT));
    }

    @Test
    void onlyRestaurantsOpenAtTheTargetedMomentAreReturned() {
        // 2026-11-28 is a Saturday; the second restaurant is closed on Saturday.
        Restaurant openSaturday = TestRestaurants.of("sat", "1 rue sat", TestRestaurants.CENTER, 100,
                TestRestaurants.everyDay("11:00", "15:00"));
        Restaurant closedSaturday = TestRestaurants.of("mon", "1 rue mon", TestRestaurants.CENTER, 100,
                java.util.Map.of("MONDAY", List.of(new hr.emocio.recoresto.search.restaurant.OpeningPeriod("11:00",
                        "15:00"))));
        sourceAnswers(answer(criteria("2026-11-28T12:00", null), List.of(opera),
                List.of(openSaturday, closedSaturday)));

        assertThat(service.search(request, false).deck()).extracting(Restaurant::name).containsExactly("sat");
    }

    @Test
    void aMomentInThePastIsNotUnderstood() {
        sourceAnswers(answer(criteria("2026-11-23T20:00", null), List.of(opera), restaurants("r", 1)));

        assertThatThrownBy(() -> service.search(request, false)).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.NOT_UNDERSTOOD));
    }

    @Test
    void deckHasAtMostTheDeckSizeWhileTheZoneKeepsEverything() {
        sourceAnswers(answer(criteria("now", null), List.of(opera), restaurants("r", 20)));

        SearchResult result = service.search(request, true);

        assertThat(result.deck()).hasSize(15);
        assertThat(result.zone()).hasSize(20);
    }

    @Test
    void severalPlacesGiveOneSetWithoutDuplicates() {
        Position chatelet = new Position(48.8584, 2.3470);
        sourceAnswers(answer(criteria("now", null), List.of(opera, chatelet),
                List.of(open("A"), open("B"), open("B"), open("C"))));

        assertThat(service.search(request, false).deck()).extracting(Restaurant::name).containsExactly("A", "B", "C");
    }

    @Test
    void restaurantsOutsideTheRadiusOrWithoutAddressAreNotReturned() {
        Restaurant far = TestRestaurants.of("far", "1 rue far", TestRestaurants.CENTER, 3000,
                TestRestaurants.everyDay("00:00", "23:59"));
        Restaurant noAddress = TestRestaurants.of("none", null, TestRestaurants.CENTER, 100,
                TestRestaurants.everyDay("00:00", "23:59"));
        sourceAnswers(answer(criteria("now", null), List.of(opera), List.of(far, noAddress, open("ok"))));

        assertThat(service.search(request, false).deck()).extracting(Restaurant::name).containsExactly("ok");
    }

    @Test
    void aSentenceThatIsNotUnderstoodIsReported() {
        sourceAnswers(SearchAnswer.notUnderstood());

        assertThatThrownBy(() -> service.search(request, false)).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.NOT_UNDERSTOOD));
    }

    @Test
    void aPlaceOutsideFranceIsReportedWithTheStatedMessage() {
        sourceAnswers(SearchAnswer.placeOutsideFrance());

        assertThatThrownBy(() -> service.search(request, false)).isInstanceOfSatisfying(SearchException.class,
                e -> {
                    assertThat(e.code()).isEqualTo(SearchErrorCode.OUTSIDE_FRANCE);
                    assertThat(e.getMessage()).isEqualTo("Nappe n'est disponible qu'en France pour l'instant");
                });
    }

    @Test
    void noPlaceFoundGivesPlaceRequired() {
        sourceAnswers(answer(criteria("now", null), List.of(), restaurants("r", 1)));

        assertThatThrownBy(() -> service.search(request, false)).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.PLACE_REQUIRED));
    }

    @Test
    void noMatchingRestaurantGivesNoResult() {
        Restaurant tooFar = TestRestaurants.of("far", "1 rue far", TestRestaurants.CENTER, 9000,
                TestRestaurants.everyDay("00:00", "23:59"));
        sourceAnswers(answer(criteria("now", null), List.of(opera), List.of(tooFar)));

        assertThatThrownBy(() -> service.search(request, false)).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.NO_RESULT));
    }

    @Test
    void aSourceWithNoRestaurantAtAllGivesNoResult() {
        sourceAnswers(answer(criteria("now", null), List.of(opera), List.of()));

        assertThatThrownBy(() -> service.search(request, false)).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.NO_RESULT));
    }

    @Test
    void aServiceFailureOfTheSourceIsNotHidden() {
        when(source.find(request.sentence()))
                .thenThrow(new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "down"));

        assertThatThrownBy(() -> service.search(request, false)).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.SERVICE_UNAVAILABLE));
    }

    private List<String> logsOf(Runnable action, Level level) {
        Logger logger = (Logger) LoggerFactory.getLogger(SearchService.class);
        Level before = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(level);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(before);
        }
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void everyStepOfASearchIsLogged() {
        Restaurant tooFar = TestRestaurants.of("far", "1 rue far", TestRestaurants.CENTER, 9000,
                TestRestaurants.everyDay("00:00", "23:59"));
        sourceAnswers(answer(criteria("now", null), List.of(opera), List.of(open("ok"), tooFar, open("ok2"))));

        List<String> messages = logsOf(() -> service.search(request, false), Level.INFO);

        assertThat(messages).anySatisfy(m -> assertThat(m).contains("Search received"));
        assertThat(messages).anySatisfy(m -> assertThat(m).contains("understood=true").contains("centers=1")
                .contains("restaurants=3"));
        assertThat(messages).anySatisfy(m -> assertThat(m).contains("radius").contains("2500"));
        assertThat(messages).anySatisfy(m -> assertThat(m).contains("Filters kept 2 of 3"));
    }

    @Test
    void theSentenceIsLoggedOnlyAtDebugLevel() {
        sourceAnswers(answer(criteria("now", null), List.of(opera), restaurants("r", 1)));

        assertThat(logsOf(() -> service.search(request, false), Level.INFO))
                .noneSatisfy(m -> assertThat(m).contains("asiatique près d'Opéra"));
        assertThat(logsOf(() -> service.search(request, false), Level.DEBUG))
                .anySatisfy(m -> assertThat(m).contains("asiatique près d'Opéra"));
    }
}
