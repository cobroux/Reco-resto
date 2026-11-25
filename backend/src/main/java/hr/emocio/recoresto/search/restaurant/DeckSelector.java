package hr.emocio.recoresto.search.restaurant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Merges the results of one or several places without duplicates and cuts the deck. The order of the source is kept:
 * the relevance rule is not defined yet (plan.md O9).
 */
@Component
public class DeckSelector {

    public record Selection(List<Restaurant> deck, List<Restaurant> zone) {
    }

    public Selection select(List<List<Restaurant>> resultsPerPlace, int deckSize) {
        Map<String, Restaurant> merged = new LinkedHashMap<>();
        for (List<Restaurant> results : resultsPerPlace) {
            for (Restaurant restaurant : results) {
                merged.putIfAbsent(key(restaurant), restaurant);
            }
        }
        List<Restaurant> zone = List.copyOf(merged.values());
        return new Selection(zone.stream().limit(deckSize).toList(), zone);
    }

    private static String key(Restaurant restaurant) {
        return (restaurant.name().trim() + "|" + restaurant.address().trim()).toLowerCase(Locale.ROOT);
    }
}
