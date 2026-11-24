package hr.emocio.recoresto.search.llm;

import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.config.LlmProperties;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Client of an OpenAI-compatible chat-completions endpoint. */
@Component
public class LlmChatClient {

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
        try {
            String body = mapper.writeValueAsString(Map.of("model", model, "messages", List.of(
                    Map.of("role", "system", "content", systemMessage),
                    Map.of("role", "user", "content", userMessage))));
            String response = client.post().uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class);
            JsonNode content = mapper.readTree(response).path("choices").path(0).path("message").path("content");
            if (!content.isString()) {
                throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "The language model gave no answer");
            }
            return content.asString();
        } catch (RestClientException | JacksonException e) {
            throw new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "The language model is unavailable", e);
        }
    }
}
