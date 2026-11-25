package hr.emocio.recoresto.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM endpoint settings (OpenAI-compatible).
 *
 * @param maxTokens largest number of tokens of an answer; the endpoint cuts an answer at 2000 tokens when none is
 *        asked for, which truncated the list of restaurants; null or 0 leaves the default of the endpoint
 * @param emptyAnswerRetries how many times the same request is sent again when the model answers with a blank
 *        message (seen on 2026-11-25); null or 0 means no new attempt
 */
@ConfigurationProperties("llm")
public record LlmProperties(String baseUrl, String apiKey, String model, Integer maxTokens,
        Integer emptyAnswerRetries) {
}
