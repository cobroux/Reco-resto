package hr.emocio.recoresto.search.restaurant;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import java.util.List;

/**
 * What the restaurant source gives back for a sentence: whether it was understood, whether the place is outside
 * France, the criteria it understood, the search points it used (one per place) and the restaurants found.
 */
public record SearchAnswer(boolean understood, boolean outsideFrance, SearchCriteria criteria, List<Position> centers,
        List<Restaurant> restaurants) {

    public SearchAnswer {
        centers = centers == null ? List.of() : List.copyOf(centers);
        restaurants = restaurants == null ? List.of() : List.copyOf(restaurants);
    }

    public static SearchAnswer notUnderstood() {
        return new SearchAnswer(false, false, null, List.of(), List.of());
    }

    public static SearchAnswer placeOutsideFrance() {
        return new SearchAnswer(true, true, null, List.of(), List.of());
    }
}
