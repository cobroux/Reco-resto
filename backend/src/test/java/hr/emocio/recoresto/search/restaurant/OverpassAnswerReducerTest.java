package hr.emocio.recoresto.search.restaurant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** What the language model gets from Overpass: only what a restaurant of the answer is made of. */
class OverpassAnswerReducerTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final OverpassAnswerReducer reducer = new OverpassAnswerReducer(mapper);

    private JsonNode reduce(String overpassJson) {
        return reducer.reduce(mapper.readTree(overpassJson));
    }

    private static final String HEADER = "\"version\":0.6,\"generator\":\"Overpass API\","
            + "\"osm3s\":{\"copyright\":\"ODbL\"},";

    @Test
    void theHeaderOfTheOverpassAnswerIsDropped() {
        JsonNode reduced = reduce("{" + HEADER + "\"elements\":[]}");

        assertThat(reduced.has("generator")).isFalse();
        assertThat(reduced.has("osm3s")).isFalse();
        assertThat(reduced.path("elements").isArray()).isTrue();
    }

    @Test
    void aNodeKeepsItsTypeIdAndCoordinates() {
        JsonNode node = reduce("{\"elements\":[{\"type\":\"node\",\"id\":7,\"lat\":48.87,\"lon\":2.33,"
                + "\"tags\":{\"name\":\"Chez Marco\"}}]}").path("elements").get(0);

        assertThat(node.path("type").asString()).isEqualTo("node");
        assertThat(node.path("id").asLong()).isEqualTo(7);
        assertThat(node.path("lat").asDouble()).isEqualTo(48.87);
        assertThat(node.path("lon").asDouble()).isEqualTo(2.33);
    }

    @Test
    void aWayOrRelationKeepsItsCenter() {
        JsonNode way = reduce("{\"elements\":[{\"type\":\"way\",\"id\":8,\"center\":{\"lat\":48.86,\"lon\":2.34},"
                + "\"nodes\":[1,2,3],\"tags\":{\"name\":\"La Table\"}}]}").path("elements").get(0);

        assertThat(way.path("center").path("lat").asDouble()).isEqualTo(48.86);
        assertThat(way.path("center").path("lon").asDouble()).isEqualTo(2.34);
        assertThat(way.has("nodes")).isFalse();
    }

    @Test
    void onlyTheTagsThatMakeUpARestaurantAreKept() {
        String tags = "{\"name\":\"Chez Marco\",\"amenity\":\"restaurant\",\"cuisine\":\"coffee_shop;italian\","
                + "\"diet:vegetarian\":\"yes\",\"addr:housenumber\":\"3\",\"addr:street\":\"Rue X\","
                + "\"addr:postcode\":\"75002\",\"addr:city\":\"Paris\",\"phone\":\"+33 1\","
                + "\"contact:phone\":\"+33 2\",\"opening_hours\":\"Mo-Su 12:00-23:00\",\"image\":\"http://i\","
                + "\"wheelchair\":\"yes\",\"source\":\"survey\",\"check_date\":\"2025-01-01\"}";

        JsonNode kept = reduce("{\"elements\":[{\"type\":\"node\",\"id\":1,\"lat\":1,\"lon\":2,\"tags\":" + tags
                + "}]}").path("elements").get(0).path("tags");

        assertThat(kept.propertyNames()).containsExactlyInAnyOrder("name", "cuisine", "diet:vegetarian",
                "addr:housenumber", "addr:street", "addr:postcode", "addr:city", "phone", "contact:phone",
                "opening_hours", "image");
    }

    @Test
    void anElementWithoutANameIsDroppedBecauseItCannotBeARestaurantOfTheAnswer() {
        JsonNode elements = reduce("{\"elements\":[{\"type\":\"node\",\"id\":1,\"lat\":1,\"lon\":2},"
                + "{\"type\":\"node\",\"id\":2,\"lat\":1,\"lon\":2,\"tags\":{\"cuisine\":\"thai\"}},"
                + "{\"type\":\"node\",\"id\":3,\"lat\":1,\"lon\":2,\"tags\":{\"name\":\" \"}},"
                + "{\"type\":\"node\",\"id\":4,\"lat\":1,\"lon\":2,\"tags\":{\"name\":\"Ok\"}}]}").path("elements");

        assertThat(elements).hasSize(1);
        assertThat(elements.get(0).path("id").asLong()).isEqualTo(4);
    }

    @Test
    void theReducedAnswerIsMuchSmallerThanTheOverpassAnswer() {
        StringBuilder elements = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            elements.append(i == 0 ? "" : ",").append("{\"type\":\"node\",\"id\":").append(i)
                    .append(",\"lat\":48.87,\"lon\":2.33,\"timestamp\":\"2020-01-01T00:00:00Z\",\"version\":3,"
                            + "\"changeset\":123456,\"user\":\"someone\",\"uid\":42,\"tags\":{\"name\":\"R").append(i)
                    .append("\",\"source\":\"survey\",\"wheelchair\":\"yes\",\"check_date\":\"2025-01-01\"}}");
        }
        String raw = "{" + HEADER + "\"elements\":[" + elements + "]}";

        assertThat(reducer.reduce(mapper.readTree(raw)).toString().length()).isLessThan(raw.length() / 2);
    }
}
