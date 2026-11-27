package hr.emocio.recoresto.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.api.SearchRequest;
import hr.emocio.recoresto.search.config.ChainProperties;
import hr.emocio.recoresto.search.config.SearchProperties;
import hr.emocio.recoresto.search.config.TestCardEnrichmentProperties;
import hr.emocio.recoresto.search.config.ToolSearchProperties;
import hr.emocio.recoresto.search.criteria.CriteriaMatcher;
import hr.emocio.recoresto.search.criteria.MomentResolver;
import hr.emocio.recoresto.search.google.GooglePlaceMapper;
import hr.emocio.recoresto.search.google.GooglePlacesClient;
import hr.emocio.recoresto.search.llm.LlmChatClient;
import hr.emocio.recoresto.search.restaurant.DeckSelector;
import hr.emocio.recoresto.search.restaurant.Restaurant;
import hr.emocio.recoresto.search.restaurant.RestaurantFilters;
import hr.emocio.recoresto.search.restaurant.SearchResult;
import hr.emocio.recoresto.search.tool.SearchRestaurantsTool;
import hr.emocio.recoresto.search.tool.ToolRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The tool-calling search, with the model and Google replaced by mocks; filters and deck are the real ones. */
class SearchServiceTest {

    private static final Position HERE = new Position(48.8700, 2.3300);
    /** Thursday 2026-11-26, 12:00 in Paris. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-26T11:00:00Z"), ZoneOffset.UTC);
    private static final int DECK_SIZE = 4;
    private static final ToolSearchProperties LIMITS = new ToolSearchProperties(300, 5000, 100, 120, 20, 3, 2);

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final LlmChatClient llm = mock(LlmChatClient.class);
    private final GooglePlacesClient google = mock(GooglePlacesClient.class);
    private SearchService service;

    @BeforeEach
    void setUp() {
        service = serviceWithDeckSize(DECK_SIZE);
    }

    /** The service as Spring builds it: the registry holds the restaurant search tool, the real filters and deck. */
    private SearchService serviceWithDeckSize(int deckSize) {
        SearchProperties properties = new SearchProperties(2.5, deckSize, 5);
        SearchRestaurantsTool searchTool = new SearchRestaurantsTool(google,
                new GooglePlaceMapper(TestCardEnrichmentProperties.defaults()),
                new RestaurantFilters(new CriteriaMatcher()), new DeckSelector(new ChainProperties(null, null)),
                new MomentResolver(CLOCK), properties, LIMITS, mapper);
        return new SearchService(llm, new ToolRegistry(List.of(searchTool)), searchTool, properties, LIMITS, mapper,
                CLOCK);
    }

    private SearchResult search(String sentence) {
        return service.search(new SearchRequest(sentence, HERE), false);
    }

    /** Google places "Resto 1".."Resto n", all restaurants with an address, 100 m apart from the position. */
    private void googleFinds(int count) {
        List<JsonNode> places = IntStream.rangeClosed(1, count).mapToObj(i -> json("""
                {"id":"p%d","displayName":{"text":"Resto %d"},"primaryType":"restaurant",
                 "location":{"latitude":%s,"longitude":2.33},"shortFormattedAddress":"%d rue X"}"""
                .formatted(i, i, 48.8700 + i * 0.0009, i))).toList();
        when(google.searchText(anyString(), any(), anyDouble(), anyList())).thenReturn(places);
    }

    private JsonNode toolCall(String arguments) {
        return json("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"c1\",\"type\":\"function\",\"function\":"
                + "{\"name\":\"search_restaurants\",\"arguments\":" + mapper.writeValueAsString(arguments) + "}}]}");
    }

    private JsonNode answer(String content) {
        return json("{\"role\":\"assistant\",\"content\":" + mapper.writeValueAsString(content) + "}");
    }

    private JsonNode json(String text) {
        return mapper.readTree(text);
    }

    private static List<String> names(SearchResult result) {
        return result.deck().stream().map(Restaurant::name).toList();
    }

    @Test
    void theModelSearchesThenItsPicksComeFirstWithTheirReasonAndTheDeckIsCompleted() {
        googleFinds(3);
        when(llm.chat(anyList(), anyList())).thenReturn(
                toolCall("{\"query\":\"burger\",\"price_levels\":[\"inexpensive\",\"cheap\"],\"radius_m\":800}"),
                answer("{\"picks\":[{\"id\":\"r2\",\"reason\":\"Burger maison\"}]}"));

        SearchResult result = search("un burger pas cher à moins de 800 m");

        verify(google).searchText("burger", HERE, 800.0, List.of("PRICE_LEVEL_INEXPENSIVE"));
        assertThat(names(result)).containsExactly("Resto 2", "Resto 1", "Resto 3");
        assertThat(result.deck()).extracting(Restaurant::reason).containsExactly("Burger maison", null, null);
        assertThat(result.criteria().query()).isEqualTo("burger");
        assertThat(result.criteria().distanceLimitMeters()).isEqualTo(800);
        assertThat(result.criteria().moment()).isEqualTo("now");
        assertThat(result.zone()).isNull();
    }

    @Test
    void theDeckStopsAtTheDeckSizeAndTheZoneHoldsEveryCandidateWhenAsked() {
        googleFinds(6);
        when(llm.chat(anyList(), anyList())).thenReturn(toolCall("{\"query\":\"pizza\"}"),
                answer("{\"picks\":[{\"id\":\"r6\",\"reason\":\"a\"},{\"id\":\"r5\",\"reason\":\"b\"}]}"));

        SearchResult result = service.search(new SearchRequest("pizza", HERE), true);

        assertThat(names(result)).containsExactly("Resto 6", "Resto 5", "Resto 1", "Resto 2");
        assertThat(result.zone()).hasSize(6);
    }

    @Test
    void unknownRepeatedOrMalformedIdsAreIgnoredAndALongReasonIsCut() {
        googleFinds(2);
        String longReason = "x".repeat(200);
        when(llm.chat(anyList(), anyList())).thenReturn(toolCall("{\"query\":\"pizza\"}"),
                answer("Voici : {\"picks\":[{\"id\":\"r9\"},{\"id\":\"p1\"},{\"id\":\"r2\",\"reason\":\"" + longReason
                        + "\"},{\"id\":\"r2\",\"reason\":\"again\"}]}"));

        SearchResult result = search("pizza");

        assertThat(names(result)).containsExactly("Resto 2", "Resto 1");
        assertThat(result.deck().getFirst().reason()).hasSize(120);
    }

    @Test
    void theToolArgumentsAreCheckedBeforeGoogleIsCalled() {
        googleFinds(1);
        when(llm.chat(anyList(), anyList())).thenReturn(
                toolCall("{\"radius_m\":50,\"diets\":[\"kosher\"],\"open_at\":\"2020-01-01T12:00\"}"),
                answer("{\"picks\":[]}"));

        SearchResult result = search("n'importe quoi");

        // no query: any restaurant; radius below 300 m: 300 m; unknown diet dropped; past moment: now
        verify(google).searchText("restaurant", HERE, 300.0, List.of());
        assertThat(result.criteria().diets()).isEmpty();
        assertThat(result.criteria().moment()).isEqualTo("now");
    }

    @Test
    void aLargeRadiusIsCappedAndATravelTimeIsWalked() {
        googleFinds(1);
        when(llm.chat(anyList(), anyList())).thenReturn(toolCall("{\"query\":\"a\",\"radius_m\":99999}"),
                answer("{\"picks\":[]}"), toolCall("{\"query\":\"b\",\"walking_minutes\":10}"),
                answer("{\"picks\":[]}"));

        search("a");
        search("b");

        verify(google).searchText("a", HERE, 5000.0, List.of());
        verify(google).searchText("b", HERE, 833.0, List.of());
    }

    @Test
    void theModelReadsCompactCandidatesWithShortIds() {
        googleFinds(2);
        when(llm.chat(anyList(), anyList())).thenReturn(toolCall("{\"query\":\"pizza\"}"), answer("{\"picks\":[]}"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object>> messages = ArgumentCaptor.forClass(List.class);

        search("pizza");

        verify(llm, times(2)).chat(messages.capture(), anyList());
        Object toolResult = messages.getValue().stream().filter(m -> m instanceof Map<?, ?> map
                && "tool".equals(map.get("role"))).map(m -> ((Map<?, ?>) m).get("content")).findFirst().orElseThrow();
        JsonNode candidates = json((String) toolResult);
        assertThat(candidates.path("count").asInt()).isEqualTo(2);
        assertThat(candidates.path("candidates").path(0).path("id").asString()).isEqualTo("r1");
        assertThat(candidates.path("candidates").path(0).path("name").asString()).isEqualTo("Resto 1");
        assertThat(candidates.path("candidates").path(0).has("address")).isFalse();
    }

    @Test
    void withoutPositionTheSearchIsRefusedBeforeAnyRequest() {
        assertThatThrownBy(() -> service.search(new SearchRequest("pizza", null), false))
                .isInstanceOfSatisfying(SearchException.class,
                        e -> assertThat(e.code()).isEqualTo(SearchErrorCode.PLACE_REQUIRED));
        verify(llm, never()).chat(anyList(), anyList());
    }

    @Test
    void whenTheModelFailsTheRawSentenceIsSearchedInBackendOrder() {
        googleFinds(2);
        when(llm.chat(anyList(), anyList()))
                .thenThrow(new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "down"));

        SearchResult result = search("ramen");

        verify(google).searchText("ramen", HERE, 2500.0, List.of());
        assertThat(names(result)).containsExactly("Resto 1", "Resto 2");
    }

    /** A sentence the model does not search for is not a restaurant request ("blablabla"). */
    @Test
    void whenTheModelAnswersWithoutSearchingTheSentenceIsNotUnderstood() {
        when(llm.chat(anyList(), anyList())).thenReturn(answer("Je ne comprends pas."));

        assertThatThrownBy(() -> search("blablabla")).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.NOT_UNDERSTOOD));
        verify(google, never()).searchText(anyString(), any(), anyDouble(), anyList());
    }

    @Test
    void anUnreadableFinalAnswerKeepsTheBackendOrder() {
        googleFinds(2);
        when(llm.chat(anyList(), anyList())).thenReturn(toolCall("{\"query\":\"pizza\"}"), answer("pas du JSON"));

        assertThat(names(search("pizza"))).containsExactly("Resto 1", "Resto 2");
    }

    @Test
    void aThirdSearchIsRefusedAndTheLastCandidatesAreUsed() {
        googleFinds(1);
        when(llm.chat(anyList(), anyList())).thenReturn(toolCall("{\"query\":\"a\"}"), toolCall("{\"query\":\"b\"}"),
                toolCall("{\"query\":\"c\"}"));

        assertThat(names(search("a"))).containsExactly("Resto 1");
        verify(llm, times(3)).chat(anyList(), anyList());
        verify(google, times(2)).searchText(anyString(), any(), anyDouble(), anyList());
        verify(google, never()).searchText(eq("c"), any(), anyDouble(), anyList());
    }

    /** Spec 009 US2: a call to a tool that does not exist gets the error text and does not use up a search. */
    @Test
    void anUnknownToolGetsTheErrorTextAndDoesNotUseUpASearch() {
        googleFinds(1);
        JsonNode unknown = json("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"c0\",\"type\":\"function\","
                + "\"function\":{\"name\":\"book_table\",\"arguments\":\"{}\"}}]}");
        when(llm.chat(anyList(), anyList())).thenReturn(unknown, toolCall("{\"query\":\"a\"}"),
                answer("{\"picks\":[]}"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object>> messages = ArgumentCaptor.forClass(List.class);

        assertThat(names(search("a"))).containsExactly("Resto 1");

        verify(llm, times(3)).chat(messages.capture(), anyList());
        assertThat(messages.getValue()).anySatisfy(m -> assertThat(m).isInstanceOfSatisfying(Map.class,
                map -> assertThat(String.valueOf(map.get("content"))).contains("No more searches")));
        verify(google, times(1)).searchText(anyString(), any(), anyDouble(), anyList());
    }

    /** Spec 009 US2: every model request carries the definitions of all the registered tools. */
    @Test
    void everyRequestCarriesTheDefinitionsOfTheRegisteredTools() {
        googleFinds(1);
        when(llm.chat(anyList(), anyList())).thenReturn(toolCall("{\"query\":\"a\"}"), answer("{\"picks\":[]}"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object>> tools = ArgumentCaptor.forClass(List.class);

        search("a");

        verify(llm, times(2)).chat(anyList(), tools.capture());
        assertThat(tools.getValue()).hasSize(1);
        assertThat(tools.getValue().getFirst().toString()).contains("search_restaurants");
    }

    /** PR #18 review: an empty second search must not discard the candidates of the first one. */
    @Test
    void anEmptySecondSearchKeepsTheCandidatesOfTheFirst() {
        googleFinds(2);
        when(google.searchText(eq("b"), any(), anyDouble(), anyList())).thenReturn(new ArrayList<>());
        when(llm.chat(anyList(), anyList())).thenReturn(toolCall("{\"query\":\"a\"}"), toolCall("{\"query\":\"b\"}"),
                answer("{\"picks\":[{\"id\":\"r2\",\"reason\":\"ok\"}]}"));

        assertThat(names(search("a"))).containsExactly("Resto 2", "Resto 1");
    }

    /** The tool price names come from the single Google price table. */
    @Test
    void everyGooglePriceLevelCanBeAskedByTheModel() {
        googleFinds(1);
        when(llm.chat(anyList(), anyList())).thenReturn(
                toolCall("{\"query\":\"a\",\"price_levels\":[\"moderate\",\"VERY_EXPENSIVE\"]}"),
                answer("{\"picks\":[]}"));

        search("a");

        verify(google).searchText("a", HERE, 2500.0, List.of("PRICE_LEVEL_MODERATE", "PRICE_LEVEL_VERY_EXPENSIVE"));
    }

    /** PR #18 review: the number of candidates never caps a deck size above 20. */
    @Test
    void aDeckSizeAboveTwentyIsHonoured() {
        service = serviceWithDeckSize(25);
        googleFinds(25); // up to about 2.5 km away: searched within 5 km
        when(llm.chat(anyList(), anyList())).thenReturn(toolCall("{\"query\":\"a\",\"radius_m\":5000}"),
                answer("{\"picks\":[]}"));

        assertThat(search("a").deck()).hasSize(25);
    }

    /** FR-016: the INFO logs hold counts and durations, never the sentence, the criteria or the restaurants. */
    @Test
    void theInfoLogsHoldNoUserTextNorRestaurant() {
        googleFinds(2);
        when(llm.chat(anyList(), anyList())).thenReturn(
                toolCall("{\"query\":\"ramen secret\",\"diets\":[\"halal\"]}"), answer("{\"picks\":[]}"),
                toolCall("{\"query\":\"ramen secret\"}"),
                answer("{\"picks\":[{\"id\":\"r1\",\"reason\":\"raison secrete\"}]}"));
        // The search loop and the restaurant search tool (moved by spec 009) both log.
        Logger logger = (Logger) LoggerFactory.getLogger(SearchService.class);
        Logger toolLogger = (Logger) LoggerFactory.getLogger(SearchRestaurantsTool.class);
        Level before = logger.getLevel();
        Level toolBefore = toolLogger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        toolLogger.addAppender(appender);
        logger.setLevel(Level.INFO);
        toolLogger.setLevel(Level.INFO);
        try {
            // halal: no candidate left, the search ends in NO_RESULT; then a search that finds restaurants
            assertThatThrownBy(() -> search("ramen secret halal")).isInstanceOf(SearchException.class);
            assertThat(names(search("ramen secret"))).contains("Resto 1");
        } finally {
            logger.detachAppender(appender);
            toolLogger.detachAppender(appender);
            logger.setLevel(before);
            toolLogger.setLevel(toolBefore);
        }

        assertThat(appender.list).isNotEmpty().allSatisfy(event -> assertThat(event.getFormattedMessage())
                .doesNotContain("secret", "halal", "Resto", "rue X", "secrete"));
    }

    @Test
    void noCandidateIsNoResult() {
        when(google.searchText(anyString(), any(), anyDouble(), anyList())).thenReturn(new ArrayList<>());
        when(llm.chat(anyList(), anyList())).thenReturn(toolCall("{\"query\":\"pizza\"}"), answer("{\"picks\":[]}"));

        assertThatThrownBy(() -> search("pizza")).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.NO_RESULT));
    }
}
