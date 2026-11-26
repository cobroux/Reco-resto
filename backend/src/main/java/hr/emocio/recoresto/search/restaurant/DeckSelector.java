package hr.emocio.recoresto.search.restaurant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Merges the results of one or several places without duplicates and cuts the deck. The order of the source is kept:
 * the relevance rule is not defined yet (plan.md O9; spec 008 D10: rule unchanged). A restaurant is identified by its
 * place identifier when it has one (spec 008), else by its name and address; a restaurant found around several places
 * keeps its first position and its nearest distance.
 */
@Component
public class DeckSelector {

    public record Selection(List<Restaurant> deck, List<Restaurant> zone) {
    }

    public Selection select(List<List<Restaurant>> resultsPerPlace, int deckSize) {
        Map<String, Restaurant> merged = new LinkedHashMap<>();
        for (List<Restaurant> results : resultsPerPlace) {
            for (Restaurant restaurant : results) {
                merged.merge(key(restaurant), restaurant, DeckSelector::nearest);
            }
        }
        List<Restaurant> zone = List.copyOf(merged.values());
        return new Selection(zone.stream().limit(deckSize).toList(), zone);
    }

    private static Restaurant nearest(Restaurant kept, Restaurant other) {
        boolean otherIsNearer = other.distanceMeters() != null
                && (kept.distanceMeters() == null || other.distanceMeters() < kept.distanceMeters());
        return otherIsNearer ? kept.withLocation(other.location(), other.distanceMeters()) : kept;
    }

    private static String key(Restaurant restaurant) {
        if (restaurant.placeId() != null && !restaurant.placeId().isBlank()) {
            return "place:" + restaurant.placeId();
        }
        return (restaurant.name().trim() + "|" + restaurant.address().trim()).toLowerCase(Locale.ROOT);
    }
}
