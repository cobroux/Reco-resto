package hr.emocio.recoresto.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM endpoint settings (OpenAI-compatible).
 *
 * @param maxTokens largest number of tokens of an answer; the endpoint cuts an answer at 2000 tokens when none is
 *        asked for, which truncated the list of restaurants; null or 0 leaves the default of the endpoint
 * @param temperature sampling temperature sent to the model; 0 makes the reading of a sentence stable (product
 *        owner, 2026-11-26: the same sentence gave different criteria); null leaves the default of the endpoint
 */
@ConfigurationProperties("llm")
public record LlmProperties(String baseUrl, String apiKey, String model, Integer maxTokens,
        Double temperature) {
}
