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

/** Spec 008 T3, T4, T8, T20 (happy path); FR-002; Text Search experiment of 2026-11-26 (the model writes a query). */
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
        LlmProperties properties = new LlmProperties("https://llm.test/v1", "secret", "nova-lite", 10000, 2, 0.0);
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
             "criteria":{"query":"asiatique ou végétarien","places":["Opéra, Paris"],"moment":"now"},
             "centers":[{"name":"Opéra, Paris","latitude":48.8708,"longitude":2.3322}]}""";

    /** FR-002, R8: one call, no tools, a prompt without any data-source query language nor type list. */
    @Test
    void oneCallWithoutToolsWithAPromptAskingForAQuery() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.tools").doesNotExist())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].content").value(SENTENCE))
                .andExpect(jsonPath("$.messages[0].content").value(Matchers.allOf(
                        Matchers.containsString("\"query\""),
                        Matchers.containsString("2026-11-24T11:00"),
                        Matchers.not(Matchers.containsStringIgnoringCase("overpass")),
                        Matchers.not(Matchers.containsStringIgnoringCase("openstreetmap")),
                        Matchers.not(Matchers.containsStringIgnoringCase("tag")))))
                .andRespond(withSuccess(reply(ASIAN_OR_VEGETARIAN), MediaType.APPLICATION_JSON));

        interpreter.interpret(SENTENCE);

        server.verify();
    }

    /** Text Search experiment (2026-11-26): the model no longer picks Google types from a closed list. */
    @Test
    void thePromptHasNoGoogleTypeList() {
        assertThat(interpreter.systemPrompt()).doesNotContain("_restaurant").doesNotContain("Google place types")
                .doesNotContain("\"cuisines\"").doesNotContain("\"combination\"");
    }

    /** The model writes a short French text of what to eat, with the words of the user. */
    @Test
    void thePromptAsksForAShortFrenchQueryKeepingTheWordsOfTheUser() {
        assertThat(interpreter.systemPrompt()).contains("\"query\": a short French search text")
                .contains("with the words of the user").contains("\"restaurant\" when the sentence says nothing");
    }

    /** "asiatique ou végétarien" is an alternative: it stays in the query, only a required diet is a diet. */
    @Test
    void thePromptSaysOnlyARequiredDietIsADiet() {
        assertThat(interpreter.systemPrompt()).contains("only the diets the user requires")
                .contains("a taste, a price, takeaway or an alternative")
                .contains("is never a diet");
    }

    /** The prompt stays short (it was about 1 400 tokens with the 115 types). */
    @Test
    void thePromptIsShort() {
        assertThat(interpreter.systemPrompt().length()).isLessThan(2600);
    }

    /** Answers seen on 2026-11-26: "ce midi" given as "now". */
    @Test
    void thePromptSaysANamedMomentIsADateTime() {
        assertThat(interpreter.systemPrompt()).contains("\"moment\" is the local").contains("never \"now\"");
    }

    /** Answer seen on 2026-11-26: "manger pas cher" read as not a request (spec 002: a budget is ignored). */
    @Test
    void thePromptSaysASentenceAboutEatingIsARequestEvenWithOnlyABudget() {
        assertThat(interpreter.systemPrompt()).contains("\"understood\" is false only when the sentence is not about")
                .contains("with only a budget or a dish, is a request");
    }

    /** A cuisine word ("Italien", "chinois") is not a place: the model is told to give no center for it. */
    @Test
    void thePromptSaysACuisineWordIsNeverAPlace() {
        assertThat(interpreter.systemPrompt()).contains("A cuisine, dish or nationality word")
                .contains("is never a place");
    }

    @Test
    void thePromptSaysNeverNull() {
        assertThat(interpreter.systemPrompt()).contains("Never write null or an empty value");
    }

    /** T3: the query and places as understood. */
    @Test
    void theUnderstoodCriteriaAndPlacesAreReturned() {
        modelAnswers(ASIAN_OR_VEGETARIAN);

        SearchAnswer answer = interpreter.interpret(SENTENCE);

        assertThat(answer.state()).isEqualTo(SearchAnswer.State.UNDERSTOOD);
        assertThat(answer.criteria().query()).isEqualTo("asiatique ou végétarien");
        assertThat(answer.criteria().diets()).isEmpty();
        assertThat(answer.places()).containsExactly(new NamedPlace("Opéra, Paris", new Position(48.8708, 2.3322)));
    }

    /** "frites": the dish is passed through, never repaired nor turned into a type. */
    @Test
    void aDishQueryIsReturnedAsItIsWithoutRepair() {
        modelAnswers("""
                {"understood":true,"criteria":{"query":"frites"},
                 "centers":[{"name":"Opéra","latitude":48.8708,"longitude":2.3322}]}""");

        assertThat(interpreter.interpret("des frites à Opéra").criteria().query()).isEqualTo("frites");
        server.verify();
    }

    /** T4: an Overpass query or filters written by the model are ignored; only the criteria are read. */
    @Test
    void filtersWrittenByTheModelAreIgnored() {
        modelAnswers("""
                {"understood":true,
                 "tags":[{"key":"cuisine","regex":"thai"}],"includedTypes":["bar"],
                 "criteria":{"query":"thaï","places":["Opéra"],"filters":["name~x"],"cuisines":["bar"]},
                 "centers":[{"name":"Opéra","latitude":48.8708,"longitude":2.3322}]}""");

        SearchAnswer answer = interpreter.interpret("un thaï à Opéra");

        assertThat(answer.state()).isEqualTo(SearchAnswer.State.UNDERSTOOD);
        assertThat(answer.criteria()).isEqualTo(new SearchCriteria("thaï", List.of(), List.of("Opéra"), null, null,
                null));
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

    // --- Repair of an unusable answer (T045: T18, T19, D11, Q3) ---

    private static final String VALID = """
            {"understood":true,"criteria":{"query":"thaï","places":["Opéra"]},
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
        assertThat(answer.criteria().query()).isEqualTo("thaï");
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

    /** "Not a request" is a readable answer: it is never repaired. */
    @Test
    void aReadableNotARequestIsNotRepaired() {
        modelAnswers("{\"understood\":false}");

        assertThat(interpreter.interpret("blablabla").state()).isEqualTo(SearchAnswer.State.NOT_A_REQUEST);
        server.verify();
    }
}
