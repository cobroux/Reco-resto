package hr.emocio.recoresto.search.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.config.LlmProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class LlmChatClientTest {

    private MockRestServiceServer server;
    private LlmChatClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LlmChatClient(builder, new LlmProperties("https://llm.test/v1/", "secret", "nova-lite", 10000, 2),
                JsonMapper.builder().build());
    }

    @Test
    void sendsChatCompletionRequestAndReturnsTheAssistantContent() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret"))
                .andExpect(jsonPath("$.model").value("nova-lite"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("be brief"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("Bonjour !"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hello\"}}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.complete("be brief", "Bonjour !")).isEqualTo("hello");
        server.verify();
    }

    @Test
    void theRequestAsksForTheConfiguredMaximumNumberOfOutputTokens() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andExpect(jsonPath("$.max_tokens").value(10000))
                .andRespond(withSuccess(FINAL_REPLY, MediaType.APPLICATION_JSON));

        client.complete("s", "u");

        server.verify();
    }

    @Test
    void withoutAConfiguredLimitTheRequestLeavesTheDefaultOfTheEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer other = MockRestServiceServer.bindTo(builder).build();
        LlmChatClient noLimit = new LlmChatClient(builder,
                new LlmProperties("https://llm.test/v1", "secret", "nova-lite", null, null), JsonMapper.builder().build());
        other.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andExpect(jsonPath("$.max_tokens").doesNotExist())
                .andRespond(withSuccess(FINAL_REPLY, MediaType.APPLICATION_JSON));

        noLimit.complete("s", "u");

        other.verify();
    }

    private static final String EMPTY_REPLY =
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"\\n\"}}]}";

    @Test
    void anEmptyAnswerIsAskedAgainAndTheLaterAnswerIsUsed() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withSuccess(EMPTY_REPLY, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withSuccess(FINAL_REPLY, MediaType.APPLICATION_JSON));

        assertThat(client.complete("s", "u")).isEqualTo("done");

        server.verify();
    }

    @Test
    void anAnswerThatStaysEmptyAfterTheRetriesIsReportedAsServiceUnavailable() {
        for (int i = 0; i < 3; i++) { // the first request and 2 retries
            server.expect(requestTo("https://llm.test/v1/chat/completions"))
                    .andRespond(withSuccess(EMPTY_REPLY, MediaType.APPLICATION_JSON));
        }

        assertThatThrownBy(() -> client.complete("s", "u")).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.SERVICE_UNAVAILABLE));

        server.verify();
    }

    @Test
    void withoutConfiguredRetriesAnEmptyAnswerIsReportedAtOnce() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer other = MockRestServiceServer.bindTo(builder).build();
        LlmChatClient noRetry = new LlmChatClient(builder,
                new LlmProperties("https://llm.test/v1", "secret", "nova-lite", null, null), JsonMapper.builder().build());
        other.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withSuccess(EMPTY_REPLY, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> noRetry.complete("s", "u")).isInstanceOf(SearchException.class);

        other.verify();
    }

    @Test
    void theFinishReasonAndTheTokensUsedAreLoggedAtDebugLevel() {
        server.expect(requestTo("https://llm.test/v1/chat/completions")).andRespond(withSuccess(
                "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"role\":\"assistant\",\"content\":\"x\"}}],"
                        + "\"usage\":{\"completion_tokens\":2000}}", MediaType.APPLICATION_JSON));

        List<String> messages = logsOf(Level.DEBUG, () -> client.complete("s", "u"));

        assertThat(messages).anySatisfy(m -> assertThat(m).contains("finish_reason=length")
                .contains("completion_tokens=2000"));
    }

    @Test
    void serverErrorIsReportedAsServiceUnavailable() {
        server.expect(requestTo("https://llm.test/v1/chat/completions")).andRespond(withServerError());

        assertThatThrownBy(() -> client.complete("s", "u")).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.SERVICE_UNAVAILABLE));
    }

    @Test
    void replyWithoutChoicesIsReportedAsServiceUnavailable() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete("s", "u")).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.SERVICE_UNAVAILABLE));
    }

    private static final LlmChatClient.Tool OVERPASS = new LlmChatClient.Tool("overpass", "Runs an Overpass query",
            Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string")), "required",
                    List.of("query")));

    private static final String TOOL_CALL_REPLY = """
            {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
              {"id":"call_1","type":"function","function":{"name":"overpass","arguments":"{\\"query\\":\\"Q1\\"}"}}]}}]}""";

    private static final String FINAL_REPLY =
            "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"done\"}}]}";

    @Test
    void toolCallRoundTripSendsTheToolResultBackAndReturnsTheFinalAnswer() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andExpect(jsonPath("$.tools[0].type").value("function"))
                .andExpect(jsonPath("$.tools[0].function.name").value("overpass"))
                .andRespond(withSuccess(TOOL_CALL_REPLY, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].id").value("call_1"))
                .andExpect(jsonPath("$.messages[3].role").value("tool"))
                .andExpect(jsonPath("$.messages[3].tool_call_id").value("call_1"))
                .andExpect(jsonPath("$.messages[3].content").value("result of Q1"))
                .andRespond(withSuccess(FINAL_REPLY, MediaType.APPLICATION_JSON));
        List<String> executed = new ArrayList<>();

        String answer = client.complete("s", "u", List.of(OVERPASS), (name, arguments) -> {
            executed.add(name + ":" + arguments);
            return "result of Q1";
        }, 3);

        assertThat(answer).isEqualTo("done");
        assertThat(executed).containsExactly("overpass:{\"query\":\"Q1\"}");
        server.verify();
    }

    /** The endpoint (Bedrock behind a proxy) refuses a history with tool calls when `tools` is missing. */
    @Test
    void toolsAreStillDeclaredOnceTheToolCallBudgetIsSpent() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andExpect(jsonPath("$.tools").exists())
                .andRespond(withSuccess(TOOL_CALL_REPLY, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andExpect(jsonPath("$.tools").exists())
                .andRespond(withSuccess(FINAL_REPLY, MediaType.APPLICATION_JSON));

        assertThat(client.complete("s", "u", List.of(OVERPASS), (name, arguments) -> "r", 1)).isEqualTo("done");
        server.verify();
    }

    @Test
    void aModelThatKeepsCallingToolsAfterTheBudgetGetsALimitMessageThenAnError() {
        // budget 1: one call runs, then two more rounds are answered with "limit reached", then the client gives up
        for (int i = 0; i < 4; i++) {
            server.expect(requestTo("https://llm.test/v1/chat/completions"))
                    .andRespond(withSuccess(TOOL_CALL_REPLY, MediaType.APPLICATION_JSON));
        }
        List<String> executed = new ArrayList<>();

        assertThatThrownBy(() -> client.complete("s", "u", List.of(OVERPASS), (name, arguments) -> {
            executed.add(arguments);
            return "r";
        }, 1)).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.SERVICE_UNAVAILABLE));

        assertThat(executed).hasSize(1);
        server.verify();
    }

    @Test
    void aToolCallBeyondTheBudgetIsNotExecuted() {
        String twoCalls = """
                {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
                  {"id":"a","type":"function","function":{"name":"overpass","arguments":"{}"}},
                  {"id":"b","type":"function","function":{"name":"overpass","arguments":"{}"}}]}}]}""";
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withSuccess(twoCalls, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[4].tool_call_id").value("b"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("limit")))
                .andRespond(withSuccess(FINAL_REPLY, MediaType.APPLICATION_JSON));
        List<String> executed = new ArrayList<>();

        client.complete("s", "u", List.of(OVERPASS), (name, arguments) -> {
            executed.add(arguments);
            return "r";
        }, 1);

        assertThat(executed).hasSize(1);
        server.verify();
    }

    @Test
    void anAnswerWithToolCallsWhenNoToolWasOfferedIsReportedAsServiceUnavailable() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withSuccess(TOOL_CALL_REPLY, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete("s", "u")).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.SERVICE_UNAVAILABLE));
    }

    private List<String> logsOf(Level level, Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(LlmChatClient.class);
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
    void requestsAndToolCallsAreLoggedAtInfoLevelWithoutTheirContent() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withSuccess(TOOL_CALL_REPLY, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withSuccess(FINAL_REPLY, MediaType.APPLICATION_JSON));

        List<String> messages = logsOf(Level.INFO,
                () -> client.complete("s", "secret sentence", List.of(OVERPASS), (name, arguments) -> "r", 3));

        assertThat(messages).anySatisfy(m -> assertThat(m).contains("LLM request 1"));
        assertThat(messages).anySatisfy(m -> assertThat(m).contains("LLM request 2"));
        assertThat(messages).anySatisfy(m -> assertThat(m).contains("asked for tool overpass"));
        assertThat(messages).noneSatisfy(m -> assertThat(m).contains("secret sentence").contains("Q1"));
    }

    @Test
    void toolArgumentsAndTheRawReplyAreLoggedAtDebugLevel() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withSuccess(TOOL_CALL_REPLY, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withSuccess(FINAL_REPLY, MediaType.APPLICATION_JSON));

        List<String> messages = logsOf(Level.DEBUG,
                () -> client.complete("s", "u", List.of(OVERPASS), (name, arguments) -> "tool says pong", 3));

        assertThat(messages).anySatisfy(m -> assertThat(m).contains("Q1"));
        assertThat(messages).anySatisfy(m -> assertThat(m).contains("tool says pong"));
        assertThat(messages).anySatisfy(m -> assertThat(m).contains("LLM reply").contains("done"));
    }
}
