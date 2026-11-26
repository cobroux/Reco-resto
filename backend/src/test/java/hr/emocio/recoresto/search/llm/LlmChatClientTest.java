package hr.emocio.recoresto.search.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
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
import java.util.List;
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

    private static final String FINAL_REPLY =
            "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"done\"}}]}";

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
    void requestsAreLoggedAtInfoLevelWithoutTheirContent() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withSuccess(FINAL_REPLY, MediaType.APPLICATION_JSON));

        List<String> messages = logsOf(Level.INFO, () -> client.complete("s", "secret sentence"));

        assertThat(messages).anySatisfy(m -> assertThat(m).contains("LLM request 1"));
        assertThat(messages).noneSatisfy(m -> assertThat(m).contains("secret sentence"));
        assertThat(messages).noneSatisfy(m -> assertThat(m).contains("done"));
    }

    @Test
    void theRawReplyIsLoggedAtDebugLevel() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withSuccess(FINAL_REPLY, MediaType.APPLICATION_JSON));

        List<String> messages = logsOf(Level.DEBUG, () -> client.complete("s", "u"));

        assertThat(messages).anySatisfy(m -> assertThat(m).contains("LLM reply").contains("done"));
    }

    /** FR-002: no tool is ever declared to the model. */
    @Test
    void theRequestDeclaresNoTool() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andExpect(jsonPath("$.tools").doesNotExist())
                .andRespond(withSuccess(FINAL_REPLY, MediaType.APPLICATION_JSON));

        client.complete("s", "u");

        server.verify();
    }
}
