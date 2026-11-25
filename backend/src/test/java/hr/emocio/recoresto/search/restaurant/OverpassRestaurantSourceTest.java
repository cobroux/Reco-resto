package hr.emocio.recoresto.search.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.config.OverpassProperties;
import hr.emocio.recoresto.search.config.SearchProperties;
import hr.emocio.recoresto.search.llm.LlmChatClient;
import hr.emocio.recoresto.search.restaurant.OverpassClient.OverpassRefusal;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

class OverpassRestaurantSourceTest {

    private static final String FINAL_ANSWER = """
            {"understood":true,"criteria":{"cuisines":["thai"],"places":["Opéra"],"moment":"now"},
             "centers":[{"latitude":48.8708,"longitude":2.3322}],
             "restaurants":[{"name":"Thai Garden","address":"3 rue X, Paris",
               "location":{"latitude":48.8690,"longitude":2.3320}}]}""";

    /** Tool arguments in the format of the Overpass tag filters, and the query the backend builds from them. */
    private static final String ARGS = "{\"around\":[{\"radius\":300,\"latitude\":48.87,\"longitude\":2.33}],"
            + "\"tags\":[{\"key\":\"amenity\",\"value\":\"restaurant\"}]}";
    private static final String QL =
            "[out:json][timeout:30];nwr[\"amenity\"=\"restaurant\"](around:300,48.87,2.33);out center;";

    private final LlmChatClient llm = mock(LlmChatClient.class);
    private final OverpassClient overpass = mock(OverpassClient.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneOffset.UTC);
    private OverpassRestaurantSource source;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(OverpassRestaurantSource.class);

    @AfterEach
    void detachLogs() {
        logger.detachAppender(logs);
        logger.setLevel(null);
    }

    private List<String> loggedMessages() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
        source = new OverpassRestaurantSource(llm, overpass, new SearchAnswerParser(mapper),
                new OverpassProperties("https://overpass.test", 30, 500, 300, 4, null, null), new SearchProperties(2.5, 15),
                clock, mapper);
    }

    /** The model asks for the given tool arguments once, then answers; returns what the executor gave back. */
    private String modelCallsTheToolWith(String arguments) {
        String[] toolResult = new String[1];
        org.mockito.Mockito.doAnswer(invocation -> {
            LlmChatClient.ToolExecutor executor = invocation.getArgument(3);
            toolResult[0] = executor.execute("overpass", arguments);
            return FINAL_ANSWER;
        }).when(llm).complete(anyString(), anyString(), anyList(), any(), anyInt());
        source.find("thaï près d'Opéra");
        return toolResult[0];
    }

    @Test
    void runsTheQueryAskedByTheModelAndGivesTheResultBack() {
        when(overpass.query(QL)).thenReturn(mapper.readTree("{\"version\":0.6,\"generator\":\"Overpass API\","
                + "\"elements\":[{\"type\":\"node\",\"id\":7,\"lat\":48.87,\"lon\":2.33,"
                + "\"tags\":{\"name\":\"Chez Marco\",\"source\":\"survey\"}}]}"));

        String result = modelCallsTheToolWith(ARGS);

        verify(overpass).query(QL);
        assertThat(result).contains("\"id\":7").contains("Chez Marco").doesNotContain("generator")
                .doesNotContain("survey");
    }

    @Test
    void aRefusedQueryIsReportedToTheModelNotThrown() {
        when(overpass.query(QL)).thenThrow(new OverpassRefusal("The query is too long"));

        String result = modelCallsTheToolWith(ARGS);

        assertThat(result).contains("The query is too long");
    }

    @Test
    void argumentsThatAreNotAQueryAreReportedToTheModel() {
        assertThat(modelCallsTheToolWith("not json")).containsIgnoringCase("JSON");
        assertThat(modelCallsTheToolWith("{}")).contains("tags");
        org.mockito.Mockito.verifyNoInteractions(overpass);
    }

    @Test
    void aPlaceLookupIsRefusedToTheModelAndNeverReachesOverpass() {
        String placeLookup = "{\"around\":[{\"radius\":300,\"latitude\":48.87,\"longitude\":2.33}],"
                + "\"tags\":[{\"key\":\"name\",\"value\":\"Opéra\"}]}";

        String result = modelCallsTheToolWith(placeLookup);

        assertThat(result).startsWith("Refused:").contains("name").contains("cuisine");
        org.mockito.Mockito.verifyNoInteractions(overpass);
    }

    @Test
    void theInstructionsForbidLookingUpPlacesWithTheTool() {
        when(llm.complete(anyString(), anyString(), anyList(), any(), anyInt())).thenReturn(FINAL_ANSWER);

        source.find("thaï");

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(llm).complete(system.capture(), anyString(), anyList(), any(), anyInt());
        assertThat(system.getValue()).contains("Never use it to look up a place");
    }

    @Test
    void theInstructionsExplainHowToFilterByTypeOfCuisine() {
        when(llm.complete(anyString(), anyString(), anyList(), any(), anyInt())).thenReturn(FINAL_ANSWER);

        source.find("italien");

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(llm).complete(system.capture(), anyString(), anyList(), any(), anyInt());
        assertThat(system.getValue()).contains("OpenStreetMap tag cuisine").contains("italian").contains("separated by ;")
                .contains("(^|;)(italian|pizza)(;|$)").contains("The tool refuses any filter on the name tag").contains("POSIX").contains("never (?:a|b)");
    }

    @Test
    void aToolOtherThanOverpassIsRefusedToTheModel() {
        String[] toolResult = new String[1];
        when(llm.complete(anyString(), anyString(), anyList(), any(), anyInt())).thenAnswer(invocation -> {
            LlmChatClient.ToolExecutor executor = invocation.getArgument(3);
            toolResult[0] = executor.execute("browse", "{\"url\":\"http://localhost\"}");
            return FINAL_ANSWER;
        });

        source.find("thaï");

        assertThat(toolResult[0]).containsIgnoringCase("unknown");
        org.mockito.Mockito.verifyNoInteractions(overpass);
    }

    @Test
    void anOverpassFailureGivesServiceUnavailable() {
        when(overpass.query(QL)).thenThrow(new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "down"));
        when(llm.complete(anyString(), anyString(), anyList(), any(), anyInt())).thenAnswer(invocation -> {
            LlmChatClient.ToolExecutor executor = invocation.getArgument(3);
            return executor.execute("overpass", ARGS);
        });

        assertThatThrownBy(() -> source.find("thaï")).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.SERVICE_UNAVAILABLE));
    }

    @Test
    void theNumberOfOverpassCallsIsBoundedByTheConfiguration() {
        when(llm.complete(anyString(), anyString(), anyList(), any(), anyInt())).thenReturn(FINAL_ANSWER);

        source.find("thaï");

        verify(llm).complete(anyString(), anyString(), anyList(), any(), eq(4));
    }

    @Test
    void theSentenceIsTheUserMessageAndTheCurrentMomentIsInThePrompt() {
        when(llm.complete(anyString(), anyString(), anyList(), any(), anyInt())).thenReturn(FINAL_ANSWER);

        source.find("thaï près d'Opéra");

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LlmChatClient.Tool>> tools = ArgumentCaptor.forClass(List.class);
        verify(llm).complete(system.capture(), user.capture(), tools.capture(), any(), anyInt());
        assertThat(user.getValue()).isEqualTo("thaï près d'Opéra");
        assertThat(system.getValue()).contains("2026-11-24T11:00").contains("Europe/Paris").contains("2500");
        assertThat(tools.getValue()).extracting(LlmChatClient.Tool::name).containsExactly("overpass");
        // the model describes its search in JSON (tag filters of the Overpass documentation), not in Overpass QL
        assertThat(tools.getValue().get(0).parameters().toString()).contains("around").contains("tags")
                .contains("regex").doesNotContain("query");
    }

    @Test
    void theFinalAnswerIsParsedIntoASearchAnswer() {
        when(llm.complete(anyString(), anyString(), anyList(), any(), anyInt())).thenReturn(FINAL_ANSWER);

        SearchAnswer answer = source.find("thaï près d'Opéra");

        assertThat(answer.understood()).isTrue();
        assertThat(answer.criteria().cuisines()).containsExactly("thai");
        assertThat(answer.restaurants()).extracting(Restaurant::name).containsExactly("Thai Garden");
        assertThat(answer.restaurants().get(0).distanceMeters()).isBetween(190, 215);
    }

    @Test
    void anAnswerThatCannotBeReadIsNotUnderstood() {
        when(llm.complete(anyString(), anyString(), anyList(), any(), anyInt())).thenReturn("blablabla");

        assertThat(source.find("blablabla").understood()).isFalse();
    }

    @Test
    void theRestaurantsOfTheAnswerAreLoggedWithTheirAddressAndDistance() {
        when(llm.complete(anyString(), anyString(), anyList(), any(), anyInt())).thenReturn(FINAL_ANSWER);

        source.find("thaï près d'Opéra");

        assertThat(loggedMessages()).anySatisfy(message -> assertThat(message).contains("1 restaurant")
                .contains("Thai Garden").contains("3 rue X, Paris").containsPattern("\\d+ m"));
    }

    @Test
    void theNumberOfElementsReturnedByOverpassIsLogged() {
        when(overpass.query(QL)).thenReturn(mapper.readTree("{\"elements\":[{\"id\":7},{\"id\":8}]}"));

        modelCallsTheToolWith(ARGS);

        assertThat(loggedMessages()).anySatisfy(message -> assertThat(message).contains("2 elements"));
    }

    @Test
    void aSearchWithoutRestaurantIsLoggedToo() {
        when(llm.complete(anyString(), anyString(), anyList(), any(), anyInt())).thenReturn("blablabla");

        source.find("blablabla");

        assertThat(loggedMessages()).anySatisfy(message -> assertThat(message).contains("0 restaurant"));
    }

    @Test
    void theQueryAndTheFinalReplyAreLoggedAtDebugLevelAndARefusalAtInfoLevel() {
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        when(overpass.query(QL)).thenThrow(new OverpassRefusal("The query is too long"));

        modelCallsTheToolWith(ARGS);

        assertThat(loggedMessages()).anySatisfy(m -> assertThat(m).contains("Overpass query").contains(QL));
        assertThat(loggedMessages()).anySatisfy(m -> assertThat(m).contains("refused").contains("too long"));
        assertThat(loggedMessages()).anySatisfy(m -> assertThat(m).contains("Final LLM reply").contains("Thai Garden"));
    }
}
