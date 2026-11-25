package hr.emocio.recoresto.search.restaurant;

import static org.assertj.core.api.Assertions.assertThat;

import hr.emocio.recoresto.search.api.Position;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Restaurants are read from the OpenStreetMap elements, never from the words of the language model. */
class OsmRestaurantMapperTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final OsmRestaurantMapper restaurants = new OsmRestaurantMapper();

    private static final Position OPERA = new Position(48.8708, 2.3322);

    private JsonNode element(String json) {
        return mapper.readTree(json);
    }

    private List<Restaurant> map(List<Position> centers, String... elements) {
        return restaurants.map(java.util.Arrays.stream(elements).map(this::element).toList(), centers);
    }

    private static final String FULL = """
            {"type":"node","id":1,"lat":48.8690,"lon":2.3320,"tags":{
              "name":"Chez Marco","cuisine":"coffee_shop;italian",
              "addr:housenumber":"3","addr:street":"Rue X","addr:postcode":"75002","addr:city":"Paris",
              "phone":"+33 1 00 00 00 00","opening_hours":"Mo-Fr 12:00-14:30,19:00-23:00","image":"https://img/1.jpg",
              "diet:vegetarian":"yes","diet:vegan":"no","diet:halal":"yes"}}""";

    @Test
    void anElementBecomesARestaurantMadeOfItsTags() {
        Restaurant restaurant = map(List.of(OPERA), FULL).get(0);

        assertThat(restaurant.name()).isEqualTo("Chez Marco");
        assertThat(restaurant.cuisine()).isEqualTo("coffee_shop;italian");
        assertThat(restaurant.address()).isEqualTo("3 Rue X, 75002 Paris");
        assertThat(restaurant.phone()).isEqualTo("+33 1 00 00 00 00");
        assertThat(restaurant.photoUrl()).isEqualTo("https://img/1.jpg");
        assertThat(restaurant.diets()).containsExactly("halal", "vegetarian");
        assertThat(restaurant.openingHours().get("MONDAY")).containsExactly(new OpeningPeriod("12:00", "14:30"),
                new OpeningPeriod("19:00", "23:00"));
        assertThat(restaurant.location()).isEqualTo(new Position(48.8690, 2.3320));
    }

    @Test
    void whatOpenStreetMapDoesNotHaveStaysAbsentAndIsNeverInvented() {
        Restaurant restaurant = map(List.of(OPERA),
                "{\"type\":\"node\",\"id\":1,\"lat\":48.87,\"lon\":2.33,\"tags\":{\"name\":\"Bare\"}}").get(0);

        assertThat(restaurant.address()).isNull();
        assertThat(restaurant.cuisine()).isNull();
        assertThat(restaurant.phone()).isNull();
        assertThat(restaurant.photoUrl()).isNull();
        assertThat(restaurant.openingHours()).isNull();
        assertThat(restaurant.priceLevel()).isNull();
        assertThat(restaurant.signatureDish()).isNull();
        assertThat(restaurant.menu()).isNull();
        assertThat(restaurant.diets()).isEmpty();
        assertThat(restaurant.keywords()).isEmpty();
    }

    @Test
    void theAddressNeedsAStreet() {
        String noStreet = "{\"type\":\"node\",\"id\":1,\"lat\":48.87,\"lon\":2.33,\"tags\":{\"name\":\"A\","
                + "\"addr:postcode\":\"75002\",\"addr:city\":\"Paris\"}}";
        String streetOnly = "{\"type\":\"node\",\"id\":2,\"lat\":48.87,\"lon\":2.33,\"tags\":{\"name\":\"B\","
                + "\"addr:street\":\"Rue X\"}}";
        String noNumber = "{\"type\":\"node\",\"id\":3,\"lat\":48.87,\"lon\":2.33,\"tags\":{\"name\":\"C\","
                + "\"addr:street\":\"Rue X\",\"addr:city\":\"Paris\"}}";

        List<Restaurant> mapped = map(List.of(OPERA), noStreet, streetOnly, noNumber);

        assertThat(mapped.get(0).address()).isNull();
        assertThat(mapped.get(1).address()).isEqualTo("Rue X");
        assertThat(mapped.get(2).address()).isEqualTo("Rue X, Paris");
    }

    @Test
    void thePhoneFallsBackToTheContactPhone() {
        Restaurant restaurant = map(List.of(OPERA), "{\"type\":\"node\",\"id\":1,\"lat\":48.87,\"lon\":2.33,"
                + "\"tags\":{\"name\":\"A\",\"contact:phone\":\"+33 2\"}}").get(0);

        assertThat(restaurant.phone()).isEqualTo("+33 2");
    }

    @Test
    void onlyAnHttpAddressIsKeptAsThePhoto() {
        Restaurant restaurant = map(List.of(OPERA), "{\"type\":\"node\",\"id\":1,\"lat\":48.87,\"lon\":2.33,"
                + "\"tags\":{\"name\":\"A\",\"image\":\"File:Photo.jpg\"}}").get(0);

        assertThat(restaurant.photoUrl()).isNull();
    }

    @Test
    void openingHoursThatCannotBeReadAreUnknown() {
        Restaurant restaurant = map(List.of(OPERA), "{\"type\":\"node\",\"id\":1,\"lat\":48.87,\"lon\":2.33,"
                + "\"tags\":{\"name\":\"A\",\"opening_hours\":\"Mo-Fr 12:00-14:00; PH off\"}}").get(0);

        assertThat(restaurant.openingHours()).isNull();
    }

    @Test
    void aWayOrRelationIsLocatedByItsCenter() {
        Restaurant restaurant = map(List.of(OPERA), "{\"type\":\"way\",\"id\":2,\"center\":{\"lat\":48.86,"
                + "\"lon\":2.34},\"tags\":{\"name\":\"La Table\"}}").get(0);

        assertThat(restaurant.location()).isEqualTo(new Position(48.86, 2.34));
    }

    @Test
    void theDistanceIsComputedFromTheNearestSearchPoint() {
        Position chatelet = new Position(48.8584, 2.3470);

        Restaurant restaurant = map(List.of(OPERA, chatelet), FULL).get(0);

        double expected = new Position(48.8690, 2.3320).distanceMetersTo(OPERA);
        assertThat(restaurant.distanceMeters()).isEqualTo((int) Math.round(expected));
        assertThat(restaurant.distanceMeters()).isLessThan(250);
    }

    @Test
    void withoutASearchPointOrALocationThereIsNoDistance() {
        assertThat(map(List.of(), FULL).get(0).distanceMeters()).isNull();
        Restaurant noLocation = map(List.of(OPERA), "{\"type\":\"node\",\"id\":1,\"tags\":{\"name\":\"A\"}}").get(0);
        assertThat(noLocation.location()).isNull();
        assertThat(noLocation.distanceMeters()).isNull();
    }

    @Test
    void aLocationWithAnInvalidCoordinateIsDropped() {
        Restaurant restaurant = map(List.of(OPERA), "{\"type\":\"node\",\"id\":1,\"lat\":99,\"lon\":2.33,"
                + "\"tags\":{\"name\":\"A\"}}").get(0);

        assertThat(restaurant.location()).isNull();
        assertThat(restaurant.distanceMeters()).isNull();
    }

    @Test
    void anElementWithoutANameIsNotARestaurant() {
        List<Restaurant> mapped = map(List.of(OPERA), "{\"type\":\"node\",\"id\":1,\"lat\":48.87,\"lon\":2.33}",
                "{\"type\":\"node\",\"id\":2,\"lat\":48.87,\"lon\":2.33,\"tags\":{\"name\":\"  \"}}",
                "{\"type\":\"node\",\"id\":3,\"lat\":48.87,\"lon\":2.33,\"tags\":{\"name\":\"Ok\"}}");

        assertThat(mapped).extracting(Restaurant::name).containsExactly("Ok");
    }

    @Test
    void theOrderOfTheElementsIsKept() {
        List<Restaurant> mapped = map(List.of(OPERA),
                "{\"type\":\"node\",\"id\":1,\"lat\":48.87,\"lon\":2.33,\"tags\":{\"name\":\"B\"}}",
                "{\"type\":\"node\",\"id\":2,\"lat\":48.87,\"lon\":2.33,\"tags\":{\"name\":\"A\"}}");

        assertThat(mapped).extracting(Restaurant::name).containsExactly("B", "A");
    }
}
