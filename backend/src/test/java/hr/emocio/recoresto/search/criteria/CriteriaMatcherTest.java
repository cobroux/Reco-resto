package hr.emocio.recoresto.search.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import hr.emocio.recoresto.search.google.GooglePlace;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Spec 008 D8, T28; Text Search experiment of 2026-11-26: what to eat is searched by Google from the query, only the
 * diets the user requires are checked on the place.
 */
class CriteriaMatcherTest {

    private final CriteriaMatcher matcher = new CriteriaMatcher();

    private static SearchCriteria diets(String... diets) {
        return new SearchCriteria("restaurant", List.of(diets), null, null, null, null);
    }

    private static GooglePlace withVegetarian(Boolean servesVegetarianFood, String... types) {
        return new GooglePlace("id", List.of(types), null, "OPERATIONAL", servesVegetarianFood, null);
    }

    @Test
    void noDietMatchesEveryRestaurant() {
        assertThat(matcher.matches(diets(), withVegetarian(null, "restaurant"))).isTrue();
        assertThat(matcher.matches(new SearchCriteria(null, null, null, null, null, null),
                withVegetarian(false, "thai_restaurant"))).isTrue();
    }

    /** The query is searched by Google, never compared with the place types. */
    @Test
    void theQueryIsNotMatchedAgainstThePlaceTypes() {
        assertThat(matcher.matches(new SearchCriteria("pizza", null, null, null, null, null),
                withVegetarian(null, "french_restaurant"))).isTrue();
    }

    @Test
    void aDietIsSatisfiedOnlyWhenTheDietIsStated() {
        assertThat(matcher.matches(diets("vegetarian"), withVegetarian(true, "restaurant"))).isTrue();
        assertThat(matcher.matches(diets("vegetarian"), withVegetarian(null, "vegetarian_restaurant"))).isTrue();
        assertThat(matcher.matches(diets("vegetarian"), withVegetarian(false, "restaurant"))).isFalse();
        assertThat(matcher.matches(diets("vegetarian"), withVegetarian(null, "restaurant"))).isFalse();
        assertThat(matcher.matches(diets("vegan"), withVegetarian(null, "vegan_restaurant"))).isTrue();
        assertThat(matcher.matches(diets("vegan"), withVegetarian(true, "vegetarian_restaurant"))).isFalse();
        assertThat(matcher.matches(diets("halal"), withVegetarian(null, "halal_restaurant"))).isTrue();
        assertThat(matcher.matches(diets(" Vegetarian "), withVegetarian(true, "restaurant"))).isTrue();
    }

    /** D8, T28: a diet Google never states matches no place. */
    @Test
    void aDietGoogleNeverStatesMatchesNoPlace() {
        assertThat(matcher.matches(diets("kosher"), withVegetarian(true, "vegetarian_restaurant",
                "vegan_restaurant", "halal_restaurant"))).isFalse();
    }

    @Test
    void everyRequiredDietMustBeStated() {
        assertThat(matcher.matches(diets("vegetarian", "halal"), withVegetarian(true, "halal_restaurant"))).isTrue();
        assertThat(matcher.matches(diets("vegetarian", "halal"), withVegetarian(true, "restaurant"))).isFalse();
    }
}
