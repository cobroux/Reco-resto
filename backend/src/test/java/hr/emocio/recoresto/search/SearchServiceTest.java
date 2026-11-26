package hr.emocio.recoresto.search;

import static hr.emocio.recoresto.search.restaurant.TestRestaurants.open;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.api.SearchRequest;
import hr.emocio.recoresto.search.config.LlmProperties;
import hr.emocio.recoresto.search.config.ChainProperties;
import hr.emocio.recoresto.search.config.SearchProperties;
import hr.emocio.recoresto.search.criteria.CriteriaMatcher;
import hr.emocio.recoresto.search.criteria.MomentResolver;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import hr.emocio.recoresto.search.criteria.SentenceInterpreter;
import hr.emocio.recoresto.search.google.GooglePlace;
import hr.emocio.recoresto.search.llm.LlmChatClient;
import hr.emocio.recoresto.search.place.PlaceLocator;
import hr.emocio.recoresto.search.restaurant.DeckSelector;
import hr.emocio.recoresto.search.restaurant.NamedPlace;
import hr.emocio.recoresto.search.restaurant.OpeningPeriod;
import hr.emocio.recoresto.search.restaurant.Restaurant;
import hr.emocio.recoresto.search.restaurant.RestaurantFilters;
import hr.emocio.recoresto.search.restaurant.RestaurantSource;
import hr.emocio.recoresto.search.restaurant.SearchAnswer;
import hr.emocio.recoresto.search.restaurant.SearchAnswerParser;
import hr.emocio.recoresto.search.restaurant.SearchResult;
import hr.emocio.recoresto.search.restaurant.TestRestaurants;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/** Spec 008 T1, T5-T9, T11, T20, T38, T44, T45 with fakes of the interpreter and the source. */
class SearchServiceTest {

    private final SentenceInterpreter interpreter = mock(SentenceInterpreter.class);
    private final RestaurantSource source = mock(RestaurantSource.class);
    private final PlaceLocator locator = mock(PlaceLocator.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneOffset.UTC);
    private final SearchService service = serviceWith(interpreter);

    private SearchService serviceWith(SentenceInterpreter sentenceInterpreter) {
        return new SearchService(sentenceInterpreter, locator, new MomentResolver(clock), source,
                new RestaurantFilters(new CriteriaMatcher()), new DeckSelector(new ChainProperties(null, null)),
                new SearchProperties(2.5, 15, 5));
    }

    private final Position opera = new Position(48.8708, 2.3322);
    private final Position chatelet = new Position(48.8584, 2.3470);
    private final Position device = new Position(48.8600, 2.3400);
    private final SearchRequest request = new SearchRequest("asiatique près d'Opéra", null);

    private SearchCriteria criteria(String moment, Integer distanceLimitMeters) {
        return new SearchCriteria(null, null, List.of("Opéra"), moment, distanceLimitMeters, null);
    }

    private SearchCriteria criteriaWalking(Integer walkingMinutes, Integer distanceLimitMeters) {
        return new SearchCriteria(null, null, List.of("Opéra"), "now", distanceLimitMeters, walkingMinutes);
    }

    private static SearchAnswer understood(SearchCriteria criteria, NamedPlace... places) {
        return new SearchAnswer(SearchAnswer.State.UNDERSTOOD, criteria, List.of(places));
    }

    private NamedPlace atOpera() {
        return new NamedPlace("Opéra", opera);
    }

    private void interpreterAnswers(SearchRequest searched, SearchAnswer answer) {
        when(interpreter.interpret(searched.sentence())).thenReturn(answer);
    }

    private void interpreterAnswers(SearchAnswer answer) {
        interpreterAnswers(request, answer);
    }

    /** The Google place of a restaurant: operational, of type restaurant, identified by its lower-case name. */
    private static GooglePlace google(Restaurant restaurant) {
        Restaurant withId = new Restaurant(restaurant.name(), restaurant.location(), restaurant.distanceMeters(),
                restaurant.cuisine(), restaurant.diets(), restaurant.keywords(), restaurant.priceLevel(),
                restaurant.openingHours(), restaurant.photoUrl(), restaurant.signatureDish(), restaurant.address(),
                restaurant.phone(), restaurant.menu(), null,
                "id-" + restaurant.name().toLowerCase(java.util.Locale.ROOT).replace(' ', '-'), null, null, null);
        return new GooglePlace(withId.placeId(), List.of("restaurant"), "restaurant", "OPERATIONAL", null, withId);
    }

    private static List<GooglePlace> google(List<Restaurant> restaurants) {
        return restaurants.stream().map(SearchServiceTest::google).toList();
    }

    @SafeVarargs
    private void sourceFinds(List<Restaurant>... perPoint) {
        when(source.find(any(), anyList(), anyDouble())).thenReturn(List.of(perPoint).stream()
                .map(SearchServiceTest::google).toList());
    }

    private static List<Restaurant> restaurants(String prefix, int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> open(prefix + i)).toList();
    }

    private static Restaurant at(String name, int distanceMeters) {
        return TestRestaurants.of(name, "1 rue " + name, TestRestaurants.CENTER, distanceMeters,
                TestRestaurants.everyDay("00:00", "23:59"));
    }

    private static void assertCode(Runnable action, SearchErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    /** T1: the deck is made of the source restaurants, each with its place id. */
    @Test
    void returnsTheUnderstoodCriteriaAndTheRestaurantsOfTheSource() {
        SearchCriteria criteria = criteria("now", null);
        interpreterAnswers(understood(criteria, atOpera()));
        sourceFinds(restaurants("r", 2));

        SearchResult result = service.search(request, false);

        assertThat(result.criteria()).isEqualTo(criteria);
        assertThat(result.deck()).extracting(Restaurant::name).containsExactly("r1", "r2");
        assertThat(result.deck()).extracting(Restaurant::placeId).containsExactly("id-r1", "id-r2");
        assertThat(result.zone()).isNull();
        verify(source).find(criteria, List.of(opera), 2500.0);
    }

    /** T5: default radius 2.5 km. */
    @Test
    void usesTheDefaultRadiusWhenTheSentenceHasNoDistance() {
        interpreterAnswers(understood(criteria("now", null), atOpera()));
        sourceFinds(List.of(at("two", 2400), at("far", 2600)));

        assertThat(service.search(request, false).deck()).extracting(Restaurant::name).containsExactly("two");
        verify(source).find(any(), eq(List.of(opera)), eq(2500.0));
    }

    @Test
    void aDistanceInTheSentenceReplacesTheDefaultRadius() {
        interpreterAnswers(understood(criteria("now", 800), atOpera()));
        sourceFinds(List.of(at("near", 700), at("farther", 900)));

        assertThat(service.search(request, false).deck()).extracting(Restaurant::name).containsExactly("near");
        verify(source).find(any(), anyList(), eq(800.0));
    }

    /** T6: 10 minutes on foot at 5 km/h = 833 m. */
    @Test
    void aTravelTimeInTheSentenceIsAWalkingDistanceAt5KmPerHour() {
        interpreterAnswers(understood(criteriaWalking(10, null), atOpera()));
        sourceFinds(List.of(at("near", 800), at("farther", 900)));

        assertThat(service.search(request, false).deck()).extracting(Restaurant::name).containsExactly("near");
        verify(source).find(any(), anyList(), eq(833.0));
    }

    @Test
    void aDistanceInMetersWinsOverATravelTime() {
        interpreterAnswers(understood(criteriaWalking(30, 500), atOpera()));
        sourceFinds(List.of(at("near", 800)));

        assertCode(() -> service.search(request, false), SearchErrorCode.NO_RESULT);
        verify(source).find(any(), anyList(), eq(500.0));
    }

    @Test
    void onlyRestaurantsOpenAtTheTargetedMomentAreReturned() {
        // 2026-11-28 is a Saturday; the second restaurant is closed on Saturday.
        Restaurant openSaturday = TestRestaurants.of("sat", "1 rue sat", TestRestaurants.CENTER, 100,
                TestRestaurants.everyDay("11:00", "15:00"));
        Restaurant closedSaturday = TestRestaurants.of("mon", "1 rue mon", TestRestaurants.CENTER, 100,
                Map.of("MONDAY", List.of(new OpeningPeriod("11:00", "15:00"))));
        interpreterAnswers(understood(criteria("2026-11-28T12:00", null), atOpera()));
        sourceFinds(List.of(openSaturday, closedSaturday));

        assertThat(service.search(request, false).deck()).extracting(Restaurant::name).containsExactly("sat");
    }

    @Test
    void aMomentInThePastIsNotUnderstoodAndGoogleIsNotCalled() {
        interpreterAnswers(understood(criteria("2026-11-23T20:00", null), atOpera()));

        assertCode(() -> service.search(request, false), SearchErrorCode.NOT_UNDERSTOOD);
        verifyNoInteractions(source);
    }

    /** T11, T45: the deck holds the deck size; the zone holds what the source returned, no more. */
    @Test
    void deckHasAtMostTheDeckSizeWhileTheZoneKeepsWhatTheSourceReturned() {
        interpreterAnswers(understood(criteria("now", null), atOpera()));
        sourceFinds(restaurants("r", 20));

        SearchResult result = service.search(request, true);

        assertThat(result.deck()).hasSize(15);
        assertThat(result.zone()).hasSize(20);
    }

    /** T7: a place found around both places appears once. */
    @Test
    void severalPlacesAreSearchedAndMergedWithoutDuplicates() {
        interpreterAnswers(understood(criteria("now", null), atOpera(), new NamedPlace("Châtelet", chatelet)));
        sourceFinds(List.of(open("A"), open("B")), List.of(open("B"), open("C")));

        assertThat(service.search(request, false).deck()).extracting(Restaurant::name).containsExactly("A", "B", "C");
        verify(source).find(any(), eq(List.of(opera, chatelet)), anyDouble());
    }

    @Test
    void restaurantsOutsideTheRadiusOrWithoutAddressAreNotReturned() {
        Restaurant noAddress = TestRestaurants.of("none", null, TestRestaurants.CENTER, 100,
                TestRestaurants.everyDay("00:00", "23:59"));
        interpreterAnswers(understood(criteria("now", null), atOpera()));
        sourceFinds(List.of(at("far", 3000), noAddress, open("ok")));

        assertThat(service.search(request, false).deck()).extracting(Restaurant::name).containsExactly("ok");
    }

    /** T20. */
    @Test
    void aSentenceThatIsNotARequestIsNotUnderstood() {
        interpreterAnswers(new SearchAnswer(SearchAnswer.State.NOT_A_REQUEST, null, List.of()));

        assertCode(() -> service.search(request, false), SearchErrorCode.NOT_UNDERSTOOD);
        verifyNoInteractions(source);
    }

    /** T8: outside France, no Google request. */
    @Test
    void aPlaceOutsideFranceIsReportedAndTheSourceIsNeverCalled() {
        interpreterAnswers(new SearchAnswer(SearchAnswer.State.OUTSIDE_FRANCE, null, List.of()));

        assertThatThrownBy(() -> service.search(request, false)).isInstanceOfSatisfying(SearchException.class,
                e -> {
                    assertThat(e.code()).isEqualTo(SearchErrorCode.OUTSIDE_FRANCE);
                    assertThat(e.getMessage()).isEqualTo("Nappe n'est disponible qu'en France pour l'instant");
                });
        verifyNoInteractions(source);
    }

    /** T9, D14: no place in the sentence, the device position is the only search point. */
    @Test
    void withoutPlaceTheDevicePositionIsTheOnlySearchPoint() {
        SearchRequest withPosition = new SearchRequest("un bon thaï ce soir", device);
        interpreterAnswers(withPosition, understood(criteria("now", null)));
        sourceFinds(restaurants("r", 1));

        assertThat(service.search(withPosition, false).deck()).hasSize(1);
        verify(source).find(any(), eq(List.of(device)), anyDouble());
    }

    /** D14: the device position is used only when the sentence names no place. */
    @Test
    void aNamedPlaceWinsOverTheDevicePosition() {
        SearchRequest withPosition = new SearchRequest("thaï à Opéra", device);
        interpreterAnswers(withPosition, understood(criteria("now", null), atOpera()));
        sourceFinds(restaurants("r", 1));

        service.search(withPosition, false);

        verify(source).find(any(), eq(List.of(opera)), anyDouble());
    }

    @Test
    void noPlaceAndNoPositionGivesPlaceRequired() {
        interpreterAnswers(understood(criteria("now", null)));

        assertCode(() -> service.search(request, false), SearchErrorCode.PLACE_REQUIRED);
        verifyNoInteractions(source);
    }

    /** T16, T17, D13: places without a position are located before the search. */
    @Test
    void placesWithoutPositionAreLocatedBeforeTheSearch() {
        interpreterAnswers(understood(criteria("now", null), atOpera(), new NamedPlace("Châtelet", null)));
        when(locator.locate("Châtelet")).thenReturn(chatelet);
        sourceFinds(restaurants("r", 1), List.of());

        service.search(request, false);

        verify(source).find(any(), eq(List.of(opera, chatelet)), anyDouble());
    }

    /** D13 (1): a valid position of the model is kept and not located. */
    @Test
    void aValidModelPositionIsNotLocated() {
        interpreterAnswers(understood(criteria("now", null), atOpera()));
        sourceFinds(restaurants("r", 1));

        service.search(request, false);

        verifyNoInteractions(locator);
    }

    /** D13 (3): a place that cannot be located, no Google search. */
    @Test
    void aPlaceThatCannotBeLocatedGivesPlaceRequired() {
        interpreterAnswers(understood(criteria("now", null), new NamedPlace("Nulle part", null)));
        when(locator.locate("Nulle part")).thenThrow(new SearchException(SearchErrorCode.PLACE_REQUIRED,
                "A place is required"));

        assertCode(() -> service.search(new SearchRequest(request.sentence(), device), false),
                SearchErrorCode.PLACE_REQUIRED);
        verifyNoInteractions(source);
    }

    /** T8, D13: a place located outside France. */
    @Test
    void aPlaceLocatedOutsideFranceIsOutsideFrance() {
        interpreterAnswers(understood(criteria("now", null), new NamedPlace("Colisée", null)));
        when(locator.locate("Colisée")).thenThrow(new SearchException(SearchErrorCode.OUTSIDE_FRANCE,
                "Nappe n'est disponible qu'en France pour l'instant"));

        assertCode(() -> service.search(request, false), SearchErrorCode.OUTSIDE_FRANCE);
        verifyNoInteractions(source);
    }

    /** SC-003 (T042 fixture set): every recorded answer of an understood sentence ends in a deck. */
    @ParameterizedTest
    @ValueSource(strings = {"prose-around.txt", "json-fence.txt", "two-objects.txt", "only-understood-and-place.txt",
            "no-centers.txt", "out-of-range-center.txt", "missing-closing-brace.txt", "empty.txt"})
    void everyRecordedAnswerOfAnUnderstoodSentenceEndsInADeck(String file) throws Exception {
        String recorded;
        try (var stream = getClass().getResourceAsStream("/llm-answers/" + file)) {
            recorded = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        String repaired = "{\"understood\":true,\"criteria\":{\"places\":[\"Opéra, Paris\"]},"
                + "\"centers\":[{\"name\":\"Opéra, Paris\",\"latitude\":48.8708,\"longitude\":2.3322}]}";
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.complete(any(), any())).thenReturn(recorded, repaired);
        JsonMapper json = JsonMapper.builder().build();
        SentenceInterpreter recordedInterpreter = new SentenceInterpreter(llm, new SearchAnswerParser(json),
                new LlmProperties("https://llm.test/v1", "k", "m", null, 2, null), clock);
        when(locator.locate(any())).thenReturn(opera);
        sourceFinds(restaurants("r", 3));

        SearchResult result = serviceWith(recordedInterpreter).search(new SearchRequest("Je veux manger à opera",
                null), false);

        assertThat(result.deck()).hasSize(3);
    }

    /** T38. */
    @Test
    void aSourceWithNoRestaurantAtAllGivesNoResult() {
        interpreterAnswers(understood(criteria("now", null), atOpera()));
        sourceFinds(List.of());

        assertCode(() -> service.search(request, false), SearchErrorCode.NO_RESULT);
    }

    @Test
    void noMatchingRestaurantGivesNoResult() {
        interpreterAnswers(understood(criteria("now", null), atOpera()));
        sourceFinds(List.of(at("far", 9000)));

        assertCode(() -> service.search(request, false), SearchErrorCode.NO_RESULT);
    }

    @Test
    void aServiceFailureOfTheSourceIsNotHidden() {
        interpreterAnswers(understood(criteria("now", null), atOpera()));
        when(source.find(any(), anyList(), anyDouble()))
                .thenThrow(new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "down"));

        assertCode(() -> service.search(request, false), SearchErrorCode.SERVICE_UNAVAILABLE);
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
    void everyStepOfASearchIsLoggedWithCountsDurationsAndPlaceIds() {
        interpreterAnswers(understood(criteria("now", null), atOpera()));
        sourceFinds(List.of(open("ok"), at("far", 9000), open("ok2")));

        List<String> messages = logsOf(() -> service.search(request, false), Level.INFO);

        assertThat(messages).anySatisfy(m -> assertThat(m).contains("Search received"));
        assertThat(messages).anySatisfy(m -> assertThat(m).contains("UNDERSTOOD").contains("places=1"));
        assertThat(messages).anySatisfy(m -> assertThat(m).contains("radius").contains("2500"));
        assertThat(messages).anySatisfy(m -> assertThat(m).contains("Source returned 3 places").contains(" ms"));
        assertThat(messages).anySatisfy(m -> assertThat(m).contains("Filters kept 2 of 3"));
        assertThat(messages).anySatisfy(m -> assertThat(m).contains("id-ok").contains("id-ok2"));
    }

    /** T44, FR-016: Google business names and addresses are never logged. */
    @Test
    void restaurantNamesAndAddressesAreNeverLogged() {
        interpreterAnswers(understood(criteria("now", null), atOpera()));
        sourceFinds(List.of(open("Chez Secret")));

        assertThat(logsOf(() -> service.search(request, false), Level.TRACE))
                .noneSatisfy(m -> assertThat(m).containsAnyOf("Chez Secret", "1 rue Chez Secret"));
    }

    @Test
    void theSentenceIsLoggedOnlyAtDebugLevel() {
        interpreterAnswers(understood(criteria("now", null), atOpera()));
        sourceFinds(restaurants("r", 1));

        assertThat(logsOf(() -> service.search(request, false), Level.INFO))
                .noneSatisfy(m -> assertThat(m).contains("asiatique près d'Opéra"));
        assertThat(logsOf(() -> service.search(request, false), Level.DEBUG))
                .anySatisfy(m -> assertThat(m).contains("asiatique près d'Opéra"));
    }

    /** Text Search experiment: the query is logged by its length at INFO, its text only at DEBUG. */
    @Test
    void theQueryIsLoggedByItsLengthAtInfoAndItsTextOnlyAtDebug() {
        SearchCriteria aligot = new SearchCriteria("aligot aux truffes", null, List.of("Opéra"), "now", null, null);
        interpreterAnswers(understood(aligot, atOpera()));
        sourceFinds(restaurants("r", 1));

        List<String> info = logsOf(() -> service.search(request, false), Level.INFO);
        assertThat(info).anySatisfy(m -> assertThat(m).contains("query of 18 characters"));
        assertThat(info).noneSatisfy(m -> assertThat(m).contains("aligot"));
        assertThat(logsOf(() -> service.search(request, false), Level.DEBUG))
                .anySatisfy(m -> assertThat(m).contains("aligot aux truffes"));
    }
}
