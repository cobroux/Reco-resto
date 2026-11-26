package hr.emocio.recoresto.search.restaurant;

import hr.emocio.recoresto.search.config.ChainProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Merges the results of one or several places without duplicates and cuts the deck. The order of the source is kept:
 * the relevance rule is not defined yet (plan.md O9; spec 008 D10: rule unchanged). A restaurant is identified by its
 * place identifier when it has one (spec 008), else by its name and address; a restaurant found around several places
 * keeps its first position and its nearest distance.
 *
 * <p>Spec 008, amendment "chains" (product owner, 2026-11-26): the deck, not the zone, keeps at most one restaurant per
 * chain ({@link ChainIdentity}), the nearest one (smallest distance, a known distance before an unknown one, ties: the
 * first in zone order); the freed slots go to the next restaurants of the zone.
 */
@Component
public class DeckSelector {

    private static final Logger log = LoggerFactory.getLogger(DeckSelector.class);

    private final ChainIdentity chains;

    public DeckSelector(ChainProperties chainProperties) {
        this.chains = new ChainIdentity(chainProperties);
    }

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
        boolean[] kept = onePerChain(zone);
        List<Restaurant> deck = new ArrayList<>(Math.min(deckSize, zone.size()));
        for (int i = 0; i < zone.size() && deck.size() < deckSize; i++) {
            if (kept[i]) {
                deck.add(zone.get(i));
            }
        }
        // A restaurant of the first deckSize positions left out of the deck has been replaced by a later one.
        long replaced = IntStream.range(0, Math.min(deckSize, zone.size())).filter(i -> !kept[i]).count();
        if (replaced > 0) {
            log.info("Deck: {} card(s) replaced because of chains", replaced);
        }
        return new Selection(List.copyOf(deck), zone);
    }

    /** Whether each restaurant of the zone may enter the deck: the nearest of its chain, or not part of a chain. */
    private boolean[] onePerChain(List<Restaurant> zone) {
        int[] chainOf = new int[zone.size()];
        Map<String, Integer> firstWithKey = new HashMap<>();
        for (int i = 0; i < zone.size(); i++) {
            chainOf[i] = i;
            for (String key : chains.keys(zone.get(i))) {
                Integer other = firstWithKey.putIfAbsent(key, i);
                if (other != null) {
                    union(chainOf, other, i);
                }
            }
        }
        Map<Integer, Integer> nearestOfChain = new HashMap<>();
        for (int i = 0; i < zone.size(); i++) {
            nearestOfChain.merge(root(chainOf, i), i,
                    (best, candidate) -> isNearer(zone.get(candidate), zone.get(best)) ? candidate : best);
        }
        boolean[] kept = new boolean[zone.size()];
        nearestOfChain.values().forEach(i -> kept[i] = true);
        return kept;
    }

    private static void union(int[] chainOf, int a, int b) {
        int rootA = root(chainOf, a);
        int rootB = root(chainOf, b);
        chainOf[Math.max(rootA, rootB)] = Math.min(rootA, rootB);
    }

    private static int root(int[] chainOf, int i) {
        while (chainOf[i] != i) {
            chainOf[i] = chainOf[chainOf[i]];
            i = chainOf[i];
        }
        return i;
    }

    /** Strictly nearer: a known distance beats an unknown one; equal distances keep the first. */
    private static boolean isNearer(Restaurant candidate, Restaurant best) {
        return candidate.distanceMeters() != null
                && (best.distanceMeters() == null || candidate.distanceMeters() < best.distanceMeters());
    }

    private static Restaurant nearest(Restaurant kept, Restaurant other) {
        return isNearer(other, kept) ? kept.withLocation(other.location(), other.distanceMeters()) : kept;
    }

    private static String key(Restaurant restaurant) {
        if (restaurant.placeId() != null && !restaurant.placeId().isBlank()) {
            return "place:" + restaurant.placeId();
        }
        return (restaurant.name().trim() + "|" + restaurant.address().trim()).toLowerCase(Locale.ROOT);
    }
}
