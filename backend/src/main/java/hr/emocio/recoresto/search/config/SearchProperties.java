package hr.emocio.recoresto.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Search settings; the default values live in {@code application.yml} only. */
@ConfigurationProperties("search")
public record SearchProperties(double defaultRadiusKm, int deckSize) {
}
