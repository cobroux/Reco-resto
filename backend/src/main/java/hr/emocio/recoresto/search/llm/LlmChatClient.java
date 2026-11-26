package hr.emocio.recoresto.search.llm;

import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.config.LlmProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Client of an OpenAI-compatible chat-completions endpoint. The model is only asked to understand a sentence: no tool
 * is ever offered (spec 008 FR-002).
 */
@Component
public class LlmChatClient {

    private static final Logger log = LoggerFactory.getLogger(LlmChatClient.class);

    /** Long hexadecimal or token-like strings (a key or its hash) are never logged from a provider error. */
    private static final Pattern KEY_LIKE = Pattern.compile("[A-Za-z0-9_-]{32,}");

    private final RestClient client;
    private final String model;
    private final Integer maxTokens;
    private final Double temperature;
    private final int emptyAnswerRetries;
    private final JsonMapper mapper;

    public LlmChatClient(RestClient.Builder builder, LlmProperties properties, JsonMapper mapper) {
        String baseUrl = properties.baseUrl() == null ? "" : properties.baseUrl().replaceAll("/+$", "");
        this.client = builder.baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
        this.model = properties.model();
        this.maxTokens = properties.maxTokens() == null || properties.maxTokens() <= 0 ? null : properties.maxTokens();
        this.temperature = properties.temperature();
        this.emptyAnswerRetries = properties.emptyAnswerRetries() == null ? 0 : Math.max(0, properties.emptyAnswerRetries());
        this.mapper = mapper;
    }

    /**
     * Sends a system and a user message and returns the content of the answer. A blank answer is asked again up to
     * {@code llm.empty-answer-retries} times, then reported as unavailable.
     */
    public String complete(String systemMessage, String userMessage) {
        List<Object> messages = List.of(
                Map.of("role", "system", "content", systemMessage),
                Map.of("role", "user", "content", userMessage));
        int requests = 0;
        int emptyAnswers = 0;
        try {
            while (true) {
                log.info("LLM request {} ({} messages)", ++requests, messages.size());
                JsonNode message = send(messages);
                if (!message.path("content").isString() || message.path("content").asString().isBlank()) {
                    // The same request is sent again: the model sometimes answers with a blank message.
                    if (++emptyAnswers <= emptyAnswerRetries) {
                        log.warn("The LLM answered with an empty message (retry {} of {})", emptyAnswers,
                                emptyAnswerRetries);
                        continue;
                    }
                    throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "The language model gave no answer");
                }
                log.debug("LLM reply: {}", message.path("content").asString());
                return message.path("content").asString();
            }
        } catch (RestClientException | JacksonException e) {
            log.warn("LLM request failed: {}", describe(e));
            throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "The language model is unavailable", e);
        }
    }

    /**
     * What may be logged about a failed request: the HTTP status and the provider error (code, type, message) when
     * there is one, otherwise the cause class. Never the key nor the request.
     */
    private String describe(Exception e) {
        if (e instanceof HttpStatusCodeException http) {
            StringBuilder detail = new StringBuilder("HTTP ").append(http.getStatusCode().value());
            try {
                JsonNode error = mapper.readTree(http.getResponseBodyAsString()).path("error");
                for (String field : List.of("code", "type", "message")) {
                    if (error.path(field).isString() || error.path(field).isNumber()) {
                        detail.append(", ").append(field).append(' ').append(KEY_LIKE.matcher(error.path(field).asString())
                                .replaceAll("[redacted]"));
                    }
                }
            } catch (JacksonException notJson) {
                // no provider detail
            }
            return detail.toString();
        }
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return e.getClass().getSimpleName() + (cause == e ? "" : " caused by " + cause.getClass().getSimpleName());
    }

    private JsonNode send(List<Object> messages) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", messages);
        if (maxTokens != null) {
            request.put("max_tokens", maxTokens);
        }
        if (temperature != null) {
            request.put("temperature", temperature);
        }
        String response = client.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON)
                .body(mapper.writeValueAsString(request)).retrieve().body(String.class);
        JsonNode root = mapper.readTree(response);
        JsonNode usage = root.path("usage");
        log.info("LLM tokens: prompt {}, completion {}, total {}", usage.path("prompt_tokens").asString(),
                usage.path("completion_tokens").asString(), usage.path("total_tokens").asString());
        log.debug("LLM finish_reason={}", root.path("choices").path(0).path("finish_reason").asString());
        JsonNode message = root.path("choices").path(0).path("message");
        if (message.isMissingNode()) {
            throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "The language model gave no answer");
        }
        return message;
    }
}
