package hr.emocio.recoresto.search.criteria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.config.LlmProperties;
import hr.emocio.recoresto.search.google.GooglePlaceTypes;
import hr.emocio.recoresto.search.llm.LlmChatClient;
import hr.emocio.recoresto.search.restaurant.NamedPlace;
import hr.emocio.recoresto.search.restaurant.SearchAnswer;
import hr.emocio.recoresto.search.restaurant.SearchAnswerParser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** Spec 008 T3, T4, T8, T20 (happy path); FR-002; implementation decision Q3. */
class SentenceInterpreterTest {

    private static final String URL = "https://llm.test/v1/chat/completions";
    private static final String SENTENCE = "J'aimerais manger asiatique et/ou végétarien dans la zone de Opéra à Paris";

    private final JsonMapper json = JsonMapper.builder().build();
    private MockRestServiceServer server;
    private SentenceInterpreter interpreter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        LlmProperties properties = new LlmProperties("https://llm.test/v1", "secret", "nova-lite", 10000, 2);
        LlmChatClient llm = new LlmChatClient(builder, properties, json);
        interpreter = new SentenceInterpreter(llm, new SearchAnswerParser(json), properties,
                Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneOffset.UTC));
    }

    /** The LLM endpoint answer whose content is the given model answer. */
    private String reply(String content) {
        return json.writeValueAsString(java.util.Map.of("choices", List.of(java.util.Map.of("message",
                java.util.Map.of("role", "assistant", "content", content)))));
    }

    private void modelAnswers(String content) {
        server.expect(requestTo(URL)).andRespond(withSuccess(reply(content), MediaType.APPLICATION_JSON));
    }

    private static final String ASIAN_OR_VEGETARIAN = """
            {"understood":true,"outsideFrance":false,
             "criteria":{"cuisines":["asian_restaurant","chinese_restaurant"],"diets":["vegetarian"],
               "combination":{"operator":"OR","children":[
                 {"operator":"OR","children":[{"kind":"cuisine","value":"asian_restaurant"},
                                              {"kind":"cuisine","value":"chinese_restaurant"}]},
                 {"kind":"diet","value":"vegetarian"}]},
               "places":["Opéra, Paris"],"moment":"now"},
             "centers":[{"name":"Opéra, Paris","latitude":48.8708,"longitude":2.3322}]}""";

    /** FR-002, R8: one call, no tools, a prompt without any data-source query language. */
    @Test
    void oneCallWithoutToolsWithThePromptOfTheClosedCuisineList() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.tools").doesNotExist())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].content").value(SENTENCE))
                .andExpect(jsonPath("$.messages[0].content").value(Matchers.allOf(
                        Matchers.containsString("asian_restaurant"), Matchers.containsString("yakitori_restaurant"),
                        Matchers.containsString("2026-11-24T11:00"),
                        Matchers.not(Matchers.containsStringIgnoringCase("overpass")),
                        Matchers.not(Matchers.containsStringIgnoringCase("openstreetmap")),
                        Matchers.not(Matchers.containsStringIgnoringCase("tag")))))
                .andRespond(withSuccess(reply(ASIAN_OR_VEGETARIAN), MediaType.APPLICATION_JSON));

        interpreter.interpret(SENTENCE);

        server.verify();
    }

    @Test
    void theSystemPromptListsEveryCuisineOfTheClosedList() {
        assertThat(interpreter.systemPrompt()).contains(GooglePlaceTypes.CUISINE_TYPES);
    }

    /** T3: the criteria and places as understood, AND/OR kept. */
    @Test
    void theUnderstoodCriteriaAndPlacesAreReturned() {
        modelAnswers(ASIAN_OR_VEGETARIAN);

        SearchAnswer answer = interpreter.interpret(SENTENCE);

        assertThat(answer.state()).isEqualTo(SearchAnswer.State.UNDERSTOOD);
        assertThat(answer.criteria().combination().operator()).isEqualTo("OR");
        assertThat(answer.criteria().diets()).containsExactly("vegetarian");
        assertThat(answer.places()).containsExactly(new NamedPlace("Opéra, Paris", new Position(48.8708, 2.3322)));
    }

    /** T4: a query or filters written by the model are ignored; only the criteria are read. */
    @Test
    void aQueryOrFiltersWrittenByTheModelAreIgnored() {
        modelAnswers("""
                {"understood":true,"query":"[out:json];nwr[amenity=restaurant];out;",
                 "tags":[{"key":"cuisine","regex":"thai"}],"includedTypes":["bar"],
                 "criteria":{"cuisines":["thai_restaurant"],"places":["Opéra"],"filters":["name~x"]},
                 "centers":[{"name":"Opéra","latitude":48.8708,"longitude":2.3322}]}""");

        SearchAnswer answer = interpreter.interpret("un thaï à Opéra");

        assertThat(answer.state()).isEqualTo(SearchAnswer.State.UNDERSTOOD);
        assertThat(answer.criteria()).isEqualTo(new SearchCriteria(List.of("thai_restaurant"), List.of(), null,
                List.of("Opéra"), null, null, null));
    }

    /** T8. */
    @Test
    void aPlaceOutsideFranceIsReported() {
        modelAnswers("{\"understood\":true,\"outsideFrance\":true}");

        assertThat(interpreter.interpret("manger italien à Rome").state())
                .isEqualTo(SearchAnswer.State.OUTSIDE_FRANCE);
    }

    /** T20. */
    @Test
    void aSentenceThatIsNotARequestIsReported() {
        modelAnswers("{\"understood\":false}");

        assertThat(interpreter.interpret("blablabla").state()).isEqualTo(SearchAnswer.State.NOT_A_REQUEST);
    }

    /** Q3: a cuisine outside the closed list never widens the search; it ends in "no result". */
    @Test
    void aCuisineOutsideTheClosedListThatStaysInvalidAfterTheRepairsGivesNoResult() {
        for (int i = 0; i < 3; i++) { // the first answer and 2 repairs (llm.empty-answer-retries)
            modelAnswers("""
                    {"understood":true,"criteria":{"cuisines":["asian"],"places":["Opéra"]},
                     "centers":[{"name":"Opéra","latitude":48.8708,"longitude":2.3322}]}""");
        }

        assertThatThrownBy(() -> interpreter.interpret("asiatique à Opéra")).isInstanceOfSatisfying(
                SearchException.class, e -> assertThat(e.code()).isEqualTo(SearchErrorCode.NO_RESULT));
        server.verify();
    }

    @Test
    void aCuisineOutsideTheClosedListInTheTreeAlsoGivesNoResult() {
        for (int i = 0; i < 3; i++) {
            modelAnswers("""
                {"understood":true,"criteria":{"cuisines":["thai_restaurant"],
                  "combination":{"operator":"OR","children":[{"kind":"cuisine","value":"thai_restaurant"},
                                                              {"kind":"cuisine","value":"bistro"}]}},
                 "centers":[{"name":"Opéra","latitude":48.8708,"longitude":2.3322}]}""");
        }

        assertThatThrownBy(() -> interpreter.interpret("thaï ou bistrot à Opéra")).isInstanceOfSatisfying(
                SearchException.class, e -> assertThat(e.code()).isEqualTo(SearchErrorCode.NO_RESULT));
    }

    // --- Repair of an unusable answer (T045: T18, T19, D11, Q3) ---

    private static final String VALID = """
            {"understood":true,"criteria":{"cuisines":["thai_restaurant"],"places":["Opéra"]},
             "centers":[{"name":"Opéra","latitude":48.8708,"longitude":2.3322}]}""";

    private static final String BROKEN = "{\"understood\":true,\"criteria\":{\"places\":[\"Opéra\"]";

    /** D11: the model is shown its answer and asked for the JSON only; a readable answer then goes on. */
    @Test
    void anUnreadableAnswerIsRepairedByShowingTheModelItsAnswer() {
        modelAnswers(BROKEN);
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.tools").doesNotExist())
                .andExpect(jsonPath("$.messages[1].content").value(Matchers.allOf(
                        Matchers.containsString("un thaï à Opéra"), Matchers.containsString(BROKEN),
                        Matchers.containsString("JSON object only"))))
                .andRespond(withSuccess(reply(VALID), MediaType.APPLICATION_JSON));

        SearchAnswer answer = interpreter.interpret("un thaï à Opéra");

        assertThat(answer.state()).isEqualTo(SearchAnswer.State.UNDERSTOOD);
        assertThat(answer.criteria().cuisines()).containsExactly("thai_restaurant");
        server.verify();
    }

    /** T18, T19, FR-007: still unreadable after llm.empty-answer-retries (2) repairs: service unavailable. */
    @Test
    void anAnswerStillUnreadableAfterTheRepairsIsServiceUnavailable() {
        for (int i = 0; i < 3; i++) {
            modelAnswers("Je ne sais pas {");
        }

        assertThatThrownBy(() -> interpreter.interpret("un thaï à Opéra")).isInstanceOfSatisfying(
                SearchException.class, e -> assertThat(e.code()).isEqualTo(SearchErrorCode.SERVICE_UNAVAILABLE));
        server.verify();
    }

    /** Q3: a cuisine outside the closed list is repaired the same way. */
    @Test
    void aCuisineOutsideTheClosedListIsRepaired() {
        modelAnswers("""
                {"understood":true,"criteria":{"cuisines":["thai"],"places":["Opéra"]},
                 "centers":[{"name":"Opéra","latitude":48.8708,"longitude":2.3322}]}""");
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages[1].content").value(Matchers.allOf(
                        Matchers.containsString("thai"), Matchers.containsString("outside the list"))))
                .andRespond(withSuccess(reply(VALID), MediaType.APPLICATION_JSON));

        assertThat(interpreter.interpret("un thaï à Opéra").criteria().cuisines()).containsExactly("thai_restaurant");
        server.verify();
    }

    /** The repairs are counted once for the sentence, whatever made the answer unusable. */
    @Test
    void theRepairsAreCountedTogether() {
        modelAnswers(BROKEN);
        modelAnswers("{\"understood\":true,\"criteria\":{\"cuisines\":[\"thai\"]}}");
        modelAnswers(BROKEN);

        assertThatThrownBy(() -> interpreter.interpret("un thaï à Opéra")).isInstanceOf(SearchException.class);
        server.verify();
    }

    /** "Not a request" is a readable answer: it is never repaired. */
    @Test
    void aReadableNotARequestIsNotRepaired() {
        modelAnswers("{\"understood\":false}");

        assertThat(interpreter.interpret("blablabla").state()).isEqualTo(SearchAnswer.State.NOT_A_REQUEST);
        server.verify();
    }
}
