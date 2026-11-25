package hr.emocio.recoresto.search.llm;

import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.config.LlmProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Client of an OpenAI-compatible chat-completions endpoint, with optional tool calling. */
@Component
public class LlmChatClient {

    private static final Logger log = LoggerFactory.getLogger(LlmChatClient.class);

    /** A function the model may call; {@code parameters} is a JSON schema. */
    public record Tool(String name, String description, Map<String, Object> parameters) {
    }

    /** Runs a tool call asked by the model and returns the text given back to it. */
    @FunctionalInterface
    public interface ToolExecutor {
        String execute(String toolName, String argumentsJson);
    }

    /** Technical safeguard, not a business value: rounds answered with "limit reached" before giving up. */
    private static final int ROUNDS_AFTER_LIMIT = 2;

    private static final String LIMIT_REACHED = "Tool call limit reached: no more calls are possible; give your final "
            + "answer with what you have.";

    private final RestClient client;
    private final String model;
    private final JsonMapper mapper;

    public LlmChatClient(RestClient.Builder builder, LlmProperties properties, JsonMapper mapper) {
        String baseUrl = properties.baseUrl() == null ? "" : properties.baseUrl().replaceAll("/+$", "");
        this.client = builder.baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
        this.model = properties.model();
        this.mapper = mapper;
    }

    /** Sends a system and a user message and returns the content of the first answer. */
    public String complete(String systemMessage, String userMessage) {
        return complete(systemMessage, userMessage, List.of(), null, 0);
    }

    /**
     * Same, with tools the model may call. Each call is run by {@code executor} and its result sent back, until the
     * model answers. At most {@code maxToolCalls} calls are run; a call beyond that gets a "limit reached" result.
     * The tools stay declared in every request, because the endpoint refuses a history that contains tool calls when
     * {@code tools} is missing (and does not support {@code tool_choice}); a model that still asks for tools after
     * {@link #ROUNDS_AFTER_LIMIT} "limit reached" answers is reported as unavailable.
     */
    public String complete(String systemMessage, String userMessage, List<Tool> tools, ToolExecutor executor,
            int maxToolCalls) {
        List<Object> messages = new ArrayList<>(List.of(
                Map.of("role", "system", "content", systemMessage),
                Map.of("role", "user", "content", userMessage)));
        int executed = 0;
        int roundsAfterLimit = 0;
        int requests = 0;
        try {
            while (true) {
                log.info("LLM request {} ({} messages, {} tools)", ++requests, messages.size(), tools.size());
                JsonNode message = send(messages, tools);
                JsonNode toolCalls = message.path("tool_calls");
                if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                    if (!message.path("content").isString()) {
                        throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE,
                                "The language model gave no answer");
                    }
                    log.debug("LLM reply: {}", message.path("content").asString());
                    return message.path("content").asString();
                }
                if (tools.isEmpty()) {
                    throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE,
                            "The language model asked for a tool that was not offered");
                }
                if (executed >= maxToolCalls && ++roundsAfterLimit > ROUNDS_AFTER_LIMIT) {
                    throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE,
                            "The language model keeps asking for tools after the limit");
                }
                messages.add(message);
                for (JsonNode call : toolCalls) {
                    log.info("LLM asked for tool {}", call.path("function").path("name").asString());
                    log.debug("LLM tool arguments: {}", call.path("function").path("arguments").asString());
                    String result;
                    if (executed < maxToolCalls) {
                        executed++;
                        result = executor.execute(call.path("function").path("name").asString(),
                                call.path("function").path("arguments").asString());
                    } else {
                        result = LIMIT_REACHED;
                    }
                    log.debug("Tool result given back to the LLM: {}", result);
                    messages.add(Map.of("role", "tool", "tool_call_id", call.path("id").asString(), "content",
                            result));
                }
            }
        } catch (RestClientException | JacksonException e) {
            throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "The language model is unavailable", e);
        }
    }

    private JsonNode send(List<Object> messages, List<Tool> tools) {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", messages);
        if (!tools.isEmpty()) {
            request.put("tools", tools.stream().map(tool -> Map.of("type", "function", "function",
                    Map.of("name", tool.name(), "description", tool.description(), "parameters", tool.parameters())))
                    .toList());
        }
        String response = client.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON)
                .body(mapper.writeValueAsString(request)).retrieve().body(String.class);
        JsonNode message = mapper.readTree(response).path("choices").path(0).path("message");
        if (message.isMissingNode()) {
            throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "The language model gave no answer");
        }
        return message;
    }
}
