package hr.emocio.recoresto.search.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.config.LlmProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class LlmChatClientTest {

    private static final String URL = "https://llm.test/v1/chat/completions";
    private static final String FINAL_REPLY =
            "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"done\"}}]}";
    private static final List<Object> MESSAGES = List.of(Map.of("role", "user", "content", "Bonjour !"));
    private static final List<Object> TOOLS = List.of(Map.of("type", "function", "function", Map.of("name", "t")));

    private MockRestServiceServer server;
    private LlmChatClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LlmChatClient(builder, new LlmProperties("https://llm.test/v1/", "secret", "nova-lite", 10000, 0.0),
                JsonMapper.builder().build());
    }

    private JsonNode chat() {
        return client.chat(MESSAGES, TOOLS);
    }

    @Test
    void sendsTheMessagesAndTheToolsAndReturnsTheAssistantMessage() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret"))
                .andExpect(jsonPath("$.model").value("nova-lite"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("Bonjour !"))
                .andExpect(jsonPath("$.tools[0].function.name").value("t"))
                .andExpect(jsonPath("$.temperature").value(0.0))
                .andExpect(jsonPath("$.max_tokens").value(10000))
                .andRespond(withSuccess(FINAL_REPLY, MediaType.APPLICATION_JSON));

        assertThat(chat().path("content").asString()).isEqualTo("done");
        server.verify();
    }

    @Test
    void theToolCallsOfTheAnswerAreReturned() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"tool_calls\":[{\"id\":\"c1\",\"function\":{\"name\":\"t\",\"arguments\":\"{}\"}}]}}]}",
                MediaType.APPLICATION_JSON));

        assertThat(chat().path("tool_calls").path(0).path("id").asString()).isEqualTo("c1");
    }

    @Test
    void withoutAConfiguredLimitTheRequestLeavesTheDefaultOfTheEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer other = MockRestServiceServer.bindTo(builder).build();
        LlmChatClient noLimit = new LlmChatClient(builder,
                new LlmProperties("https://llm.test/v1", "secret", "nova-lite", null, null), JsonMapper.builder().build());
        other.expect(requestTo(URL))
                .andExpect(jsonPath("$.max_tokens").doesNotExist())
                .andExpect(jsonPath("$.temperature").doesNotExist())
                .andRespond(withSuccess(FINAL_REPLY, MediaType.APPLICATION_JSON));

        noLimit.chat(MESSAGES, TOOLS);

        other.verify();
    }

    @Test
    void theFinishReasonIsLoggedAtDebugLevelAndTheTokensUsedAtInfoLevel() {
        String reply = "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"x\"}}],"
                + "\"usage\":{\"prompt_tokens\":1200,\"completion_tokens\":2000,\"total_tokens\":3200}}";
        server.expect(requestTo(URL)).andRespond(withSuccess(reply, MediaType.APPLICATION_JSON));

        List<String> debug = logsOf(Level.DEBUG, this::chat);

        assertThat(debug).anySatisfy(m -> assertThat(m).contains("finish_reason=length"));
        assertThat(debug).anySatisfy(m -> assertThat(m).contains("LLM tokens: prompt 1200, completion 2000, total 3200"));
        assertThat(debug).noneSatisfy(m -> assertThat(m).contains("Bonjour"));
    }

    @Test
    void serverErrorIsReportedAsServiceUnavailable() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(this::chat).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(SearchErrorCode.SERVICE_UNAVAILABLE));
    }

    /** A refused request is logged at WARN with its HTTP status and the provider error code, never the key. */
    @Test
    void aRefusedRequestIsLoggedWithItsStatusAndErrorCode() {
        server.expect(requestTo(URL)).andRespond(
                withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON).body(
                        "{\"error\":{\"message\":\"Rate limit reached for api_key: 0ec541babdd5c1c0707abd65c088a98556e0fee3ffe4dcd0a31c71e2f0bd76b7\",\"type\":\"requests\",\"code\":\"rate_limit_exceeded\"}}"));

        List<String> messages = logsOf(Level.WARN, () -> assertThatThrownBy(this::chat)
                .isInstanceOf(SearchException.class));

        assertThat(messages).anySatisfy(m -> assertThat(m).contains("LLM request failed").contains("HTTP 429")
                .contains("rate_limit_exceeded").contains("Rate limit reached"));
        assertThat(messages).noneSatisfy(m -> assertThat(m).contains("secret"));
        assertThat(messages).noneSatisfy(m -> assertThat(m).contains("0ec541babdd5"));
    }

    /** A network failure is logged at WARN with its cause class. */
    @Test
    void aNetworkFailureIsLoggedWithItsCause() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new java.net.ConnectException("Connection refused");
        });

        List<String> messages = logsOf(Level.WARN, () -> assertThatThrownBy(this::chat)
                .isInstanceOf(SearchException.class));

        assertThat(messages).anySatisfy(m -> assertThat(m).contains("LLM request failed").contains("ConnectException"));
    }

    @Test
    void replyWithoutChoicesIsReportedAsServiceUnavailable() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(this::chat).isInstanceOfSatisfying(SearchException.class,
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
}
