package hr.emocio.recoresto.search.google;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 008 T3, T5; decisions D6, D7, D8. */
class GooglePlaceTypesTest {

    /**
     * D6: {@code restaurant} and the {@code *_restaurant} types only, judged on the Google primary type (a cinema or a
     * shopping centre can list {@code restaurant} among its secondary types).
     */
    @Test
    void onlyRestaurantAndTheRestaurantSubtypesAreRestaurants() {
        assertThat(GooglePlaceTypes.isRestaurant("restaurant")).isTrue();
        assertThat(GooglePlaceTypes.isRestaurant("italian_restaurant")).isTrue();
        assertThat(GooglePlaceTypes.isRestaurant("fast_food_restaurant")).isTrue();
        assertThat(GooglePlaceTypes.isRestaurant("cafe")).isFalse();
        assertThat(GooglePlaceTypes.isRestaurant("bar")).isFalse();
        assertThat(GooglePlaceTypes.isRestaurant("bakery")).isFalse();
        assertThat(GooglePlaceTypes.isRestaurant("movie_theater")).isFalse();
        assertThat(GooglePlaceTypes.isRestaurant("shopping_mall")).isFalse();
        assertThat(GooglePlaceTypes.isRestaurant(null)).isFalse();
    }

    /** D6: every {@code *_restaurant} type of Google Table A (115 on 2026-11-25, research R3). */
    @Test
    void theCuisineTypesAreEveryRestaurantSubtypeOfTableA() {
        assertThat(GooglePlaceTypes.CUISINE_TYPES).hasSize(115).doesNotHaveDuplicates()
                .allSatisfy(type -> assertThat(type).endsWith("_restaurant"))
                .doesNotContain("restaurant")
                .contains("afghani_restaurant", "asian_restaurant", "chinese_restaurant", "italian_restaurant",
                        "japanese_restaurant", "thai_restaurant", "vietnamese_restaurant", "korean_restaurant",
                        "indian_restaurant", "french_restaurant", "pizza_restaurant", "sushi_restaurant",
                        "ramen_restaurant", "fast_food_restaurant", "fine_dining_restaurant", "yakitori_restaurant");
    }

    /** The restaurant types are {@code restaurant} plus the cuisine types (single table). */
    @Test
    void theRestaurantTypesAreRestaurantAndTheCuisineTypes() {
        assertThat(GooglePlaceTypes.RESTAURANT_TYPES).hasSize(116).contains("restaurant")
                .containsAll(GooglePlaceTypes.CUISINE_TYPES);
    }

    /** D8: the diet indicator types. */
    @Test
    void dietIndicatorTypes() {
        assertThat(GooglePlaceTypes.DIET_TYPES).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "vegetarian", "vegetarian_restaurant", "vegan", "vegan_restaurant", "halal", "halal_restaurant"));
    }

    /** D8: the diets a place states, in alphabetical order; false or absent states nothing. */
    @Test
    void statedDiets() {
        assertThat(GooglePlaceTypes.statedDiets(List.of("halal_restaurant", "vegan_restaurant"), true))
                .containsExactly("halal", "vegan", "vegetarian");
        assertThat(GooglePlaceTypes.statedDiets(List.of("vegetarian_restaurant"), null)).containsExactly("vegetarian");
        assertThat(GooglePlaceTypes.statedDiets(List.of("restaurant"), false)).isEmpty();
        assertThat(GooglePlaceTypes.statedDiets(null, null)).isEmpty();
    }
}
