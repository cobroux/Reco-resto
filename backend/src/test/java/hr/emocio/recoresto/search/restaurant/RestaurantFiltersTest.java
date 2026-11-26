package hr.emocio.recoresto.search.restaurant;

import static hr.emocio.recoresto.search.restaurant.TestRestaurants.CENTER;
import static hr.emocio.recoresto.search.restaurant.TestRestaurants.everyDay;
import static hr.emocio.recoresto.search.restaurant.TestRestaurants.of;
import static org.assertj.core.api.Assertions.assertThat;

import hr.emocio.recoresto.search.criteria.CriteriaMatcher;
import hr.emocio.recoresto.search.criteria.CriteriaNode;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import hr.emocio.recoresto.search.google.GooglePlace;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RestaurantFiltersTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    /** A Saturday. */
    private static final ZonedDateTime SATURDAY_NOON = ZonedDateTime.of(2026, 11, 28, 12, 0, 0, 0, PARIS);

    private static final SearchCriteria NO_CRITERIA = new SearchCriteria(null, null, null, null, null, null, null);

    private final RestaurantFilters filters = new RestaurantFilters(new CriteriaMatcher());

    /** The existing rules on the restaurant: each one an operational Google restaurant, no criteria. */
    private List<Restaurant> applyToRestaurants(List<Restaurant> restaurants, double radiusMeters,
            ZonedDateTime moment) {
        return filters.apply(restaurants.stream().map(r -> place(r, "OPERATIONAL", "restaurant")).toList(),
                NO_CRITERIA, radiusMeters, moment);
    }

    private static GooglePlace place(Restaurant restaurant, String status, String... types) {
        return new GooglePlace(restaurant.name(), List.of(types), types.length == 0 ? null : types[0], status, null,
                restaurant);
    }

    private static Restaurant at(String name, int distanceMeters) {
        return of(name, "1 rue " + name, CENTER, distanceMeters, everyDay("00:00", "23:59"));
    }

    private List<String> names(List<Restaurant> restaurants) {
        return restaurants.stream().map(Restaurant::name).toList();
    }

    /** D6: a place whose primary type is not a restaurant is excluded, even when "restaurant" is among its types. */
    @Test
    void placeWhosePrimaryTypeIsNotARestaurantIsExcluded() {
        GooglePlace cinema = place(at("cinema", 100), "OPERATIONAL", "movie_theater", "restaurant", "food");
        GooglePlace mall = place(at("mall", 100), "OPERATIONAL", "shopping_mall", "restaurant");
        GooglePlace bistro = place(at("bistro", 100), "OPERATIONAL", "french_restaurant", "restaurant", "food");

        assertThat(names(filters.apply(List.of(cinema, mall, bistro), NO_CRITERIA, 2500, SATURDAY_NOON)))
                .containsExactly("bistro");
    }

    @Test
    void keepsOnlyRestaurantsWithinTheRadius() {
        List<Restaurant> all = List.of(at("near", 1000), at("edge", 2500), at("far", 3000));

        assertThat(names(applyToRestaurants(all, 2500, SATURDAY_NOON))).containsExactly("near", "edge");
        assertThat(names(applyToRestaurants(all, 5000, SATURDAY_NOON))).containsExactly("near", "edge", "far");
        assertThat(names(applyToRestaurants(all, 800, SATURDAY_NOON))).isEmpty();
    }

    @Test
    void restaurantWithoutKnownDistanceOrLocationIsExcluded() {
        Restaurant noDistance = of("a", "1 rue a", CENTER, null, everyDay("00:00", "23:59"));
        Restaurant noLocation = of("b", "1 rue b", null, 100, everyDay("00:00", "23:59"));

        assertThat(applyToRestaurants(List.of(noDistance, noLocation), 2500, SATURDAY_NOON)).isEmpty();
    }

    @Test
    void restaurantWithoutAddressIsExcluded() {
        Restaurant none = of("a", null, CENTER, 100, everyDay("00:00", "23:59"));
        Restaurant blank = of("b", "  ", CENTER, 100, everyDay("00:00", "23:59"));

        assertThat(applyToRestaurants(List.of(none, blank), 2500, SATURDAY_NOON)).isEmpty();
    }

    @Test
    void opensAtTheOpeningTimeAndClosesAtTheClosingTime() {
        Restaurant lunch = of("lunch", "1 rue l", CENTER, 100, Map.of("SATURDAY", List.of(
                new OpeningPeriod("12:00", "14:00"))));

        assertThat(applyToRestaurants(List.of(lunch), 2500, SATURDAY_NOON)).hasSize(1);
        assertThat(applyToRestaurants(List.of(lunch), 2500, SATURDAY_NOON.withHour(14))).isEmpty();
        assertThat(applyToRestaurants(List.of(lunch), 2500, SATURDAY_NOON.withHour(11))).isEmpty();
    }

    @Test
    void usesTheDayOfTheTargetedMoment() {
        Restaurant mondayOnly = of("m", "1 rue m", CENTER, 100, Map.of("MONDAY", List.of(
                new OpeningPeriod("09:00", "23:00"))));

        assertThat(applyToRestaurants(List.of(mondayOnly), 2500, SATURDAY_NOON)).isEmpty();
        assertThat(applyToRestaurants(List.of(mondayOnly), 2500, SATURDAY_NOON.plusDays(2))).hasSize(1);
    }

    /** Changed 2026-11-25 at the request of the team (spec 002 FR-007 deviation, to report in the PR). */
    @Test
    void restaurantWithoutAnyKnownOpeningHoursIsKeptBecauseItIsNotKnownToBeClosed() {
        Map<String, List<OpeningPeriod>> noPeriod = new HashMap<>();
        noPeriod.put("MONDAY", null);
        noPeriod.put("TUESDAY", List.of());

        assertThat(applyToRestaurants(List.of(of("x", "1 rue x", CENTER, 100, null)), 2500, SATURDAY_NOON)).hasSize(1);
        assertThat(applyToRestaurants(List.of(of("y", "1 rue y", CENTER, 100, Map.of())), 2500, SATURDAY_NOON))
                .hasSize(1);
        assertThat(applyToRestaurants(List.of(of("z", "1 rue z", CENTER, 100, noPeriod)), 2500, SATURDAY_NOON))
                .hasSize(1);
    }

    @Test
    void restaurantWithKnownOpeningHoursThatDoNotCoverTheMomentIsStillExcluded() {
        Map<String, List<OpeningPeriod>> mondayOnly = Map.of("MONDAY", List.of(new OpeningPeriod("12:00", "14:00")));

        assertThat(applyToRestaurants(List.of(of("x", "1 rue x", CENTER, 100, mondayOnly)), 2500, SATURDAY_NOON))
                .isEmpty();
    }

    @Test
    void aClosingTimeNotAfterTheOpeningTimeMeansUntilTheEndOfTheDay() {
        Restaurant lateNight = of("night", "1 rue n", CENTER, 100, Map.of("SATURDAY", List.of(
                new OpeningPeriod("18:00", "00:00"))));

        assertThat(applyToRestaurants(List.of(lateNight), 2500, SATURDAY_NOON.withHour(23))).hasSize(1);
        assertThat(applyToRestaurants(List.of(lateNight), 2500, SATURDAY_NOON)).isEmpty();
    }

    /** 008 D9 (3): closed and future places are excluded; operational and missing status are kept. */
    @Test
    void closedAndFuturePlacesAreExcludedOperationalAndUnknownStatusAreKept() {
        List<GooglePlace> places = List.of(place(at("operational", 100), "OPERATIONAL", "restaurant"),
                place(at("unknown", 100), null, "restaurant"),
                place(at("temporarily", 100), "CLOSED_TEMPORARILY", "restaurant"),
                place(at("permanently", 100), "CLOSED_PERMANENTLY", "restaurant"),
                place(at("future", 100), "FUTURE_OPENING", "restaurant"));

        assertThat(names(filters.apply(places, NO_CRITERIA, 2500, SATURDAY_NOON)))
                .containsExactly("operational", "unknown");
    }

    /** 008 D6: only restaurant and *_restaurant places are restaurants. */
    @Test
    void placesThatAreNotRestaurantsAreExcluded() {
        List<GooglePlace> places = List.of(place(at("restaurant", 100), "OPERATIONAL", "restaurant", "food"),
                place(at("thai", 100), "OPERATIONAL", "thai_restaurant"),
                place(at("cafe", 100), "OPERATIONAL", "cafe", "food"),
                place(at("bar", 100), "OPERATIONAL", "bar"),
                place(at("bakery", 100), "OPERATIONAL", "bakery", "store"));

        assertThat(names(filters.apply(places, NO_CRITERIA, 2500, SATURDAY_NOON)))
                .containsExactly("restaurant", "thai");
    }

    /** 008 T5: places failing the AND/OR criteria are excluded. */
    @Test
    void placesNotMatchingTheCriteriaAreExcluded() {
        SearchCriteria italian = new SearchCriteria(null, null, new CriteriaNode(null, "cuisine",
                "italian_restaurant", null), null, null, null, null);
        List<GooglePlace> places = List.of(place(at("italian", 100), "OPERATIONAL", "italian_restaurant",
                "restaurant"), place(at("thai", 100), "OPERATIONAL", "thai_restaurant", "restaurant"));

        assertThat(names(filters.apply(places, italian, 2500, SATURDAY_NOON))).containsExactly("italian");
    }

    /** 008 T10, T23 and SC-005 on Google places: address, radius and unknown hours rules unchanged. */
    @Test
    void theExistingRulesStillApplyToGooglePlaces() {
        List<GooglePlace> places = List.of(
                place(of("noAddress", null, CENTER, 100, null), "OPERATIONAL", "restaurant"),
                place(of("far", "1 rue far", CENTER, 3000, null), "OPERATIONAL", "restaurant"),
                place(of("unknownHours", "1 rue u", CENTER, 100, null), "OPERATIONAL", "restaurant"),
                place(of("closedNow", "1 rue c", CENTER, 100, Map.of("MONDAY", List.of(new OpeningPeriod("12:00",
                        "14:00")))), "OPERATIONAL", "restaurant"));

        assertThat(names(filters.apply(places, NO_CRITERIA, 2500, SATURDAY_NOON))).containsExactly("unknownHours");
    }
}
