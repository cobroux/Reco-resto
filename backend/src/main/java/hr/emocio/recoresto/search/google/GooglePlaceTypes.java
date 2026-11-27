package hr.emocio.recoresto.search.google;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The single table of the Google place types the search relies on (spec 008, research R3).
 * <ul>
 * <li>Restaurant check (D6): a place is a restaurant when its types contain {@code restaurant} or one of the
 * {@code *_restaurant} types.</li>
 * <li>Diet indicators (D8): the types and {@code servesVegetarianFood}, see {@link #statedDiets}.</li>
 * </ul>
 * Source of the list: https://developers.google.com/maps/documentation/places/web-service/place-types (Table A, "Food
 * and Drink"), read on 2026-11-25.
 */
public final class GooglePlaceTypes {

    /** The generic restaurant type. */
    public static final String RESTAURANT = "restaurant";

    /** D6: every {@code *_restaurant} type of Table A, in the order of the page. */
    public static final List<String> CUISINE_TYPES = List.of(
            "afghani_restaurant", "african_restaurant", "american_restaurant", "argentinian_restaurant",
            "asian_fusion_restaurant", "asian_restaurant", "australian_restaurant", "austrian_restaurant",
            "bangladeshi_restaurant", "barbecue_restaurant", "basque_restaurant", "bavarian_restaurant",
            "belgian_restaurant", "brazilian_restaurant", "british_restaurant", "brunch_restaurant",
            "buffet_restaurant", "burmese_restaurant", "burrito_restaurant", "cajun_restaurant",
            "californian_restaurant", "cambodian_restaurant", "cantonese_restaurant", "caribbean_restaurant",
            "chicken_restaurant", "chicken_wings_restaurant", "chilean_restaurant", "chinese_noodle_restaurant",
            "chinese_restaurant", "colombian_restaurant", "croatian_restaurant", "cuban_restaurant",
            "czech_restaurant", "danish_restaurant", "dessert_restaurant", "dim_sum_restaurant",
            "dumpling_restaurant", "dutch_restaurant", "eastern_european_restaurant", "ethiopian_restaurant",
            "european_restaurant", "falafel_restaurant", "family_restaurant", "fast_food_restaurant",
            "filipino_restaurant", "fine_dining_restaurant", "fish_and_chips_restaurant", "fondue_restaurant",
            "french_restaurant", "fusion_restaurant", "german_restaurant", "greek_restaurant", "gyro_restaurant",
            "halal_restaurant", "hamburger_restaurant", "hawaiian_restaurant", "hot_dog_restaurant",
            "hot_pot_restaurant", "hungarian_restaurant", "indian_restaurant", "indonesian_restaurant",
            "irish_restaurant", "israeli_restaurant", "italian_restaurant", "japanese_curry_restaurant",
            "japanese_izakaya_restaurant", "japanese_restaurant", "korean_barbecue_restaurant", "korean_restaurant",
            "latin_american_restaurant", "lebanese_restaurant", "malaysian_restaurant", "mediterranean_restaurant",
            "mexican_restaurant", "middle_eastern_restaurant", "mongolian_barbecue_restaurant",
            "moroccan_restaurant", "north_indian_restaurant", "oyster_bar_restaurant", "pakistani_restaurant",
            "persian_restaurant", "peruvian_restaurant", "pizza_restaurant", "polish_restaurant",
            "portuguese_restaurant", "ramen_restaurant", "romanian_restaurant", "russian_restaurant",
            "scandinavian_restaurant", "seafood_restaurant", "shawarma_restaurant", "soul_food_restaurant",
            "soup_restaurant", "south_american_restaurant", "south_indian_restaurant", "southwestern_us_restaurant",
            "spanish_restaurant", "sri_lankan_restaurant", "sushi_restaurant", "swiss_restaurant", "taco_restaurant",
            "taiwanese_restaurant", "tapas_restaurant", "tex_mex_restaurant", "thai_restaurant",
            "tibetan_restaurant", "tonkatsu_restaurant", "turkish_restaurant", "ukrainian_restaurant",
            "vegan_restaurant", "vegetarian_restaurant", "vietnamese_restaurant", "western_restaurant",
            "yakiniku_restaurant", "yakitori_restaurant");

    /** D6: the types that make a place a restaurant. */
    public static final Set<String> RESTAURANT_TYPES = restaurantTypes();

    /** D8: the diet {@code servesVegetarianFood} states. */
    public static final String VEGETARIAN = "vegetarian";

    /** D8: diet identifier of the criteria to the place type that states it. */
    public static final Map<String, String> DIET_TYPES = Map.of(
            VEGETARIAN, "vegetarian_restaurant",
            "vegan", "vegan_restaurant",
            "halal", "halal_restaurant");

    private GooglePlaceTypes() {
    }

    private static Set<String> restaurantTypes() {
        Set<String> types = new LinkedHashSet<>();
        types.add(RESTAURANT);
        types.addAll(CUISINE_TYPES);
        return Collections.unmodifiableSet(types);
    }

    /**
     * D6: whether a place with this Google primary type is a restaurant. The secondary types are not used: a cinema or
     * a shopping centre can list {@code restaurant} among them.
     */
    public static boolean isRestaurant(String primaryType) {
        return primaryType != null && RESTAURANT_TYPES.contains(primaryType);
    }

    /**
     * D8: the diets a place states, in alphabetical order: vegetarian when {@code servesVegetarianFood} is true or
     * the place has the vegetarian type; vegan and halal by their types only. False or absent states nothing (FR-009).
     */
    public static List<String> statedDiets(Collection<String> types, Boolean servesVegetarianFood) {
        Set<String> diets = new TreeSet<>();
        if (Boolean.TRUE.equals(servesVegetarianFood)) {
            diets.add(VEGETARIAN);
        }
        if (types != null) {
            DIET_TYPES.forEach((diet, type) -> {
                if (types.contains(type)) {
                    diets.add(diet);
                }
            });
        }
        return List.copyOf(diets);
    }
}
