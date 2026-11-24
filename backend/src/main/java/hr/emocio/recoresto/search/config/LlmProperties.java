package hr.emocio.recoresto.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** LLM endpoint settings (OpenAI-compatible; the base URL includes the {@code /v1} suffix). */
@ConfigurationProperties("llm")
public record LlmProperties(String baseUrl, String apiKey, String model) {
}
