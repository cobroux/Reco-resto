package hr.emocio.recoresto.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Search settings; the default values live in {@code application.yml} only.
 *
 * @param walkingSpeedKmh every travel time of a sentence is on foot (decision of the team, 2026-11-25); this speed
 *        turns minutes into a distance
 */
@ConfigurationProperties("search")
public record SearchProperties(double defaultRadiusKm, int deckSize, double walkingSpeedKmh) {

    /** The distance, in meters, walked in the given number of minutes. */
    public long walkingMeters(double minutes) {
        return Math.round(minutes * walkingSpeedKmh * 1000 / 60);
    }
}
