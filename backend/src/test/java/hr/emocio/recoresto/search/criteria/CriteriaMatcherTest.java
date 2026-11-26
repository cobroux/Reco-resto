package hr.emocio.recoresto.search.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import hr.emocio.recoresto.search.google.GooglePlace;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 008 T5, T3 (cuisine part); D7; implementation decision Q2. */
class CriteriaMatcherTest {

    private final CriteriaMatcher matcher = new CriteriaMatcher();

    private static GooglePlace place(String... types) {
        return new GooglePlace("id", List.of(types), types.length == 0 ? null : types[0], "OPERATIONAL", null, null);
    }

    private static CriteriaNode cuisine(String value) {
        return new CriteriaNode(null, "cuisine", value, null);
    }

    private static CriteriaNode diet(String value) {
        return new CriteriaNode(null, "diet", value, null);
    }

    private static CriteriaNode or(CriteriaNode... children) {
        return new CriteriaNode("OR", null, null, List.of(children));
    }

    private static CriteriaNode and(CriteriaNode... children) {
        return new CriteriaNode("AND", null, null, List.of(children));
    }

    private static SearchCriteria tree(CriteriaNode combination) {
        return new SearchCriteria(null, null, combination, null, null, null, null);
    }

    private static SearchCriteria lists(List<String> cuisines, List<String> diets) {
        return new SearchCriteria(cuisines, diets, null, null, null, null, null);
    }

    @Test
    void aCuisineLeafMatchesWhenThePlaceTypesContainThatGoogleType() {
        SearchCriteria italian = tree(cuisine("italian_restaurant"));

        assertThat(matcher.matches(italian, place("italian_restaurant", "restaurant"))).isTrue();
        assertThat(matcher.matches(italian, place("restaurant", "italian_restaurant"))).isTrue();
        assertThat(matcher.matches(italian, place("french_restaurant", "restaurant"))).isFalse();
        assertThat(matcher.matches(italian, place())).isFalse();
    }

    @Test
    void orNeedsOneChildAndNeedsEveryChild() {
        SearchCriteria either = tree(or(cuisine("italian_restaurant"), cuisine("pizza_restaurant")));
        SearchCriteria both = tree(and(cuisine("italian_restaurant"), cuisine("pizza_restaurant")));

        assertThat(matcher.matches(either, place("pizza_restaurant"))).isTrue();
        assertThat(matcher.matches(either, place("thai_restaurant"))).isFalse();
        assertThat(matcher.matches(both, place("pizza_restaurant"))).isFalse();
        assertThat(matcher.matches(both, place("pizza_restaurant", "italian_restaurant"))).isTrue();
    }

    @Test
    void nestedTreesAreEvaluated() {
        SearchCriteria criteria = tree(and(or(cuisine("thai_restaurant"), cuisine("chinese_restaurant")),
                cuisine("fast_food_restaurant")));

        assertThat(matcher.matches(criteria, place("chinese_restaurant", "fast_food_restaurant"))).isTrue();
        assertThat(matcher.matches(criteria, place("chinese_restaurant"))).isFalse();
    }

    @Test
    void noCriteriaMatchesEveryRestaurant() {
        assertThat(matcher.matches(tree(null), place("restaurant"))).isTrue();
        assertThat(matcher.matches(lists(null, null), place("french_restaurant"))).isTrue();
    }

    /** Q2: without a tree, OR between the cuisines, AND with the diets. */
    @Test
    void listsWithoutATreeAreCuisinesOrTogetherAndDietsAndTogether() {
        assertThat(CriteriaMatcher.tree(lists(List.of("italian_restaurant", "pizza_restaurant"),
                List.of("vegetarian")))).isEqualTo(and(or(cuisine("italian_restaurant"), cuisine("pizza_restaurant")),
                        diet("vegetarian")));
        assertThat(CriteriaMatcher.tree(lists(List.of("thai_restaurant"), null))).isEqualTo(
                and(or(cuisine("thai_restaurant"))));
        assertThat(CriteriaMatcher.tree(lists(null, null))).isNull();

        SearchCriteria italianOrPizza = lists(List.of("italian_restaurant", "pizza_restaurant"), null);
        assertThat(matcher.matches(italianOrPizza, place("pizza_restaurant"))).isTrue();
        assertThat(matcher.matches(italianOrPizza, place("thai_restaurant"))).isFalse();
    }

    @Test
    void theTreeWinsOverTheLists() {
        SearchCriteria criteria = new SearchCriteria(List.of("thai_restaurant"), null,
                cuisine("italian_restaurant"), null, null, null, null);

        assertThat(CriteriaMatcher.tree(criteria)).isEqualTo(cuisine("italian_restaurant"));
        assertThat(matcher.matches(criteria, place("italian_restaurant"))).isTrue();
    }

    @Test
    void operatorsAreReadWhateverTheirCase() {
        SearchCriteria criteria = tree(new CriteriaNode("or", null, null, List.of(cuisine("thai_restaurant"),
                cuisine("korean_restaurant"))));

        assertThat(matcher.matches(criteria, place("korean_restaurant"))).isTrue();
    }

    @Test
    void cuisineValuesOfTheListsAndTheTreeAreCollected() {
        SearchCriteria criteria = new SearchCriteria(List.of("thai_restaurant"), List.of("vegan"),
                or(cuisine("italian_restaurant"), and(cuisine("pizza_restaurant"), diet("vegetarian"))), null, null,
                null, null);

        assertThat(CriteriaMatcher.cuisineValues(criteria)).containsExactlyInAnyOrder("thai_restaurant",
                "italian_restaurant", "pizza_restaurant");
    }

    // --- Diets (T035: T26, T27, T28, D8) ---

    private static GooglePlace withVegetarian(Boolean servesVegetarianFood, String... types) {
        return new GooglePlace("id", List.of(types), null, "OPERATIONAL", servesVegetarianFood, null);
    }

    @Test
    void aDietLeafIsSatisfiedOnlyWhenTheDietIsStated() {
        SearchCriteria vegetarian = tree(diet("vegetarian"));

        assertThat(matcher.matches(vegetarian, withVegetarian(true, "restaurant"))).isTrue();
        assertThat(matcher.matches(vegetarian, withVegetarian(null, "vegetarian_restaurant"))).isTrue();
        assertThat(matcher.matches(vegetarian, withVegetarian(false, "restaurant"))).isFalse();
        assertThat(matcher.matches(vegetarian, withVegetarian(null, "restaurant"))).isFalse();
        assertThat(matcher.matches(tree(diet("vegan")), withVegetarian(null, "vegan_restaurant"))).isTrue();
        assertThat(matcher.matches(tree(diet("vegan")), withVegetarian(true, "vegetarian_restaurant"))).isFalse();
        assertThat(matcher.matches(tree(diet("halal")), withVegetarian(null, "halal_restaurant"))).isTrue();
        assertThat(matcher.matches(tree(diet("Vegetarian")), withVegetarian(true, "restaurant"))).isTrue();
    }

    /** D8: under AND an unknown diet excludes the place; under OR another branch can match. */
    @Test
    void anUnknownDietExcludesUnderAndButNotUnderOr() {
        GooglePlace thaiUnknownDiet = withVegetarian(null, "thai_restaurant", "restaurant");

        assertThat(matcher.matches(tree(and(cuisine("thai_restaurant"), diet("vegetarian"))), thaiUnknownDiet))
                .isFalse();
        assertThat(matcher.matches(tree(or(cuisine("thai_restaurant"), diet("vegetarian"))), thaiUnknownDiet))
                .isTrue();
    }

    /** D8, T28: a diet Google never states matches no place. */
    @Test
    void aDietGoogleNeverStatesMatchesNoPlace() {
        assertThat(matcher.matches(tree(diet("kosher")), withVegetarian(true, "vegetarian_restaurant",
                "vegan_restaurant", "halal_restaurant"))).isFalse();
    }

    @Test
    void dietListsWithoutATreeMustAllBeStated() {
        SearchCriteria vegetarianAndHalal = lists(null, List.of("vegetarian", "halal"));

        assertThat(matcher.matches(vegetarianAndHalal, withVegetarian(true, "halal_restaurant"))).isTrue();
        assertThat(matcher.matches(vegetarianAndHalal, withVegetarian(true, "restaurant"))).isFalse();
    }
}
