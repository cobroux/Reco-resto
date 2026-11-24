package hr.emocio.recoresto.search.restaurant;

import static hr.emocio.recoresto.search.restaurant.TestRestaurants.CENTER;
import static hr.emocio.recoresto.search.restaurant.TestRestaurants.everyDay;
import static hr.emocio.recoresto.search.restaurant.TestRestaurants.of;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RestaurantFiltersTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    /** A Saturday. */
    private static final ZonedDateTime SATURDAY_NOON = ZonedDateTime.of(2026, 11, 28, 12, 0, 0, 0, PARIS);

    private final RestaurantFilters filters = new RestaurantFilters();

    private static Restaurant at(String name, int distanceMeters) {
        return of(name, "1 rue " + name, CENTER, distanceMeters, everyDay("00:00", "23:59"));
    }

    private List<String> names(List<Restaurant> restaurants) {
        return restaurants.stream().map(Restaurant::name).toList();
    }

    @Test
    void keepsOnlyRestaurantsWithinTheRadius() {
        List<Restaurant> all = List.of(at("near", 1000), at("edge", 2500), at("far", 3000));

        assertThat(names(filters.apply(all, 2500, SATURDAY_NOON))).containsExactly("near", "edge");
        assertThat(names(filters.apply(all, 5000, SATURDAY_NOON))).containsExactly("near", "edge", "far");
        assertThat(names(filters.apply(all, 800, SATURDAY_NOON))).isEmpty();
    }

    @Test
    void restaurantWithoutKnownDistanceOrLocationIsExcluded() {
        Restaurant noDistance = of("a", "1 rue a", CENTER, null, everyDay("00:00", "23:59"));
        Restaurant noLocation = of("b", "1 rue b", null, 100, everyDay("00:00", "23:59"));

        assertThat(filters.apply(List.of(noDistance, noLocation), 2500, SATURDAY_NOON)).isEmpty();
    }

    @Test
    void restaurantWithoutAddressIsExcluded() {
        Restaurant none = of("a", null, CENTER, 100, everyDay("00:00", "23:59"));
        Restaurant blank = of("b", "  ", CENTER, 100, everyDay("00:00", "23:59"));

        assertThat(filters.apply(List.of(none, blank), 2500, SATURDAY_NOON)).isEmpty();
    }

    @Test
    void opensAtTheOpeningTimeAndClosesAtTheClosingTime() {
        Restaurant lunch = of("lunch", "1 rue l", CENTER, 100, Map.of("SATURDAY", List.of(
                new OpeningPeriod("12:00", "14:00"))));

        assertThat(filters.apply(List.of(lunch), 2500, SATURDAY_NOON)).hasSize(1);
        assertThat(filters.apply(List.of(lunch), 2500, SATURDAY_NOON.withHour(14))).isEmpty();
        assertThat(filters.apply(List.of(lunch), 2500, SATURDAY_NOON.withHour(11))).isEmpty();
    }

    @Test
    void usesTheDayOfTheTargetedMoment() {
        Restaurant mondayOnly = of("m", "1 rue m", CENTER, 100, Map.of("MONDAY", List.of(
                new OpeningPeriod("09:00", "23:00"))));

        assertThat(filters.apply(List.of(mondayOnly), 2500, SATURDAY_NOON)).isEmpty();
        assertThat(filters.apply(List.of(mondayOnly), 2500, SATURDAY_NOON.plusDays(2))).hasSize(1);
    }

    @Test
    void restaurantWithoutOpeningHoursIsExcludedBecauseItsOpeningCannotBeConfirmed() {
        assertThat(filters.apply(List.of(of("x", "1 rue x", CENTER, 100, null)), 2500, SATURDAY_NOON)).isEmpty();
        assertThat(filters.apply(List.of(of("y", "1 rue y", CENTER, 100, Map.of())), 2500, SATURDAY_NOON))
                .isEmpty();
    }

    @Test
    void aClosingTimeNotAfterTheOpeningTimeMeansUntilTheEndOfTheDay() {
        Restaurant lateNight = of("night", "1 rue n", CENTER, 100, Map.of("SATURDAY", List.of(
                new OpeningPeriod("18:00", "00:00"))));

        assertThat(filters.apply(List.of(lateNight), 2500, SATURDAY_NOON.withHour(23))).hasSize(1);
        assertThat(filters.apply(List.of(lateNight), 2500, SATURDAY_NOON)).isEmpty();
    }
}
