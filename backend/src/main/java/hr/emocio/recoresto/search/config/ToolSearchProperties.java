package hr.emocio.recoresto.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits of the tool-calling search; the values live in {@code application.yml} only.
 *
 * @param minRadiusMeters smallest search radius the model may ask for
 * @param maxRadiusMeters largest search radius the model may ask for
 * @param maxQueryLength longest text sent to Google Text Search, in characters
 * @param maxReasonLength longest reason or dish ("À goûter") of the model shown on a card, in characters
 * @param maxCandidates candidates shown to the model (at least the deck size is always kept)
 * @param maxRounds model requests of one search
 * @param maxToolCalls searches the model may run for one sentence
 */
@ConfigurationProperties("search.tool")
public record ToolSearchProperties(int minRadiusMeters, int maxRadiusMeters, int maxQueryLength, int maxReasonLength,
        int maxCandidates, int maxRounds, int maxToolCalls) {
}
