package hr.emocio.recoresto.search.google;

import hr.emocio.recoresto.search.restaurant.Restaurant;
import java.util.List;

/**
 * What the backend keeps from one Google result before filtering (spec 008 data-model.md "Google place"). Internal:
 * never part of the response, which only carries {@link #restaurant()}.
 *
 * @param id Google place identifier ({@code id}), the deduplication key
 * @param types Google {@code types}: restaurant check (D6), diet indicators (D8)
 * @param primaryType Google {@code primaryType}
 * @param businessStatus Google {@code businessStatus}, null when absent (D9 (3))
 * @param servesVegetarianFood Google {@code servesVegetarianFood}: true, false or null when absent (D8)
 * @param restaurant the mapped response record
 */
public record GooglePlace(String id, List<String> types, String primaryType, String businessStatus,
        Boolean servesVegetarianFood, Restaurant restaurant) {

    public GooglePlace {
        types = types == null ? List.of() : List.copyOf(types);
    }
}
