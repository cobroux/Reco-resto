package hr.emocio.recoresto.search.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hr.emocio.recoresto.search.restaurant.OverpassClient.OverpassRefusal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The tool arguments follow the tag filters of the Overpass documentation (criteria/per_tag): {@code key=value} and
 * {@code key~regex}; several filters in one statement are an AND.
 */
class OverpassQueryBuilderTest {

    private final OverpassQueryBuilder builder = new OverpassQueryBuilder(JsonMapper.builder().build(), 60);

    private static final String AROUND_OPERA = "{\"radius\":2500,\"latitude\":48.8708,\"longitude\":2.3322}";
    private static final String RESTAURANT = "{\"key\":\"amenity\",\"value\":\"restaurant\"}";

    private static String args(String around, String tags) {
        return "{\"around\":[" + around + "],\"tags\":[" + tags + "]}";
    }

    @Test
    void anExactTagAroundAPointBecomesAJsonOverpassQuery() {
        assertThat(builder.build(args(AROUND_OPERA, RESTAURANT))).isEqualTo(
                "[out:json][timeout:60];nwr[\"amenity\"=\"restaurant\"](around:2500,48.8708,2.3322);out center;");
    }

    @Test
    void aRegexOnTheValueUsesTheTildeOperator() {
        String cuisine = "{\"key\":\"cuisine\",\"regex\":\"^(thai|vietnamese)$\"}";

        assertThat(builder.build(args(AROUND_OPERA, RESTAURANT + "," + cuisine)))
                .contains("[\"cuisine\"~\"^(thai|vietnamese)$\"]");
    }

    @Test
    void aCuisineRegexCanMatchOneValueInsideASemicolonSeparatedList() {
        String italian = "{\"key\":\"cuisine\",\"regex\":\"(^|;)(italian|pizza)(;|$)\"}";

        assertThat(builder.build(args(AROUND_OPERA, RESTAURANT + "," + italian)))
                .contains("[\"amenity\"=\"restaurant\"][\"cuisine\"~\"(^|;)(italian|pizza)(;|$)\"]");
    }

    @Test
    void severalFiltersAreAppliedTogetherInOneStatement() {
        String cuisine = "{\"key\":\"cuisine\",\"value\":\"thai\"}";

        assertThat(builder.build(args(AROUND_OPERA, RESTAURANT + "," + cuisine))).contains(
                "nwr[\"amenity\"=\"restaurant\"][\"cuisine\"=\"thai\"](around:2500,48.8708,2.3322);");
    }

    @Test
    void severalPointsGiveAUnionOfStatements() {
        String chatelet = "{\"radius\":800,\"latitude\":48.8584,\"longitude\":2.347}";

        assertThat(builder.build(args(AROUND_OPERA + "," + chatelet, RESTAURANT))).isEqualTo(
                "[out:json][timeout:60];("
                        + "nwr[\"amenity\"=\"restaurant\"](around:2500,48.8708,2.3322);"
                        + "nwr[\"amenity\"=\"restaurant\"](around:800,48.8584,2.347);"
                        + ");out center;");
    }

    @Test
    void elementTypesCanBeRestricted() {
        String json = "{\"types\":[\"node\",\"way\"],\"around\":[" + AROUND_OPERA + "],\"tags\":[" + RESTAURANT + "]}";

        assertThat(builder.build(json)).isEqualTo("[out:json][timeout:60];("
                + "node[\"amenity\"=\"restaurant\"](around:2500,48.8708,2.3322);"
                + "way[\"amenity\"=\"restaurant\"](around:2500,48.8708,2.3322);"
                + ");out center;");
    }

    @Test
    void quotesAndBackslashesInLiteralsAreEscaped() {
        String tag = "{\"key\":\"na\\\"me\",\"regex\":\"^L\\\\d\"}";

        assertThat(builder.build(args(AROUND_OPERA, RESTAURANT + "," + tag))).contains("[\"na\\\"me\"~\"^L\\\\d\"]");
    }

    @Test
    void coordinatesAreNeverWrittenInScientificNotation() {
        String tiny = "{\"radius\":10,\"latitude\":0.00001,\"longitude\":2.0}";

        assertThat(builder.build(args(tiny, RESTAURANT))).contains("(around:10,0.00001,2)");
    }

    private void assertRefused(String arguments, String messagePart) {
        assertThatThrownBy(() -> builder.build(arguments)).isInstanceOf(OverpassRefusal.class)
                .hasMessageContaining(messagePart);
    }

    @Test
    void argumentsThatAreNotJsonAreRefused() {
        assertRefused("not json", "JSON");
    }

    @Test
    void aSearchWithoutAPointIsRefused() {
        assertRefused("{\"tags\":[" + RESTAURANT + "]}", "around");
        assertRefused("{\"around\":[],\"tags\":[" + RESTAURANT + "]}", "around");
    }

    @Test
    void aSearchWithoutATagFilterIsRefused() {
        assertRefused("{\"around\":[" + AROUND_OPERA + "]}", "tags");
        assertRefused("{\"around\":[" + AROUND_OPERA + "],\"tags\":[]}", "tags");
    }

    @Test
    void aPointWithAnInvalidRadiusOrCoordinateIsRefused() {
        assertRefused(args("{\"radius\":0,\"latitude\":48.8,\"longitude\":2.3}", RESTAURANT), "radius");
        assertRefused(args("{\"radius\":-5,\"latitude\":48.8,\"longitude\":2.3}", RESTAURANT), "radius");
        assertRefused(args("{\"radius\":100,\"latitude\":91,\"longitude\":2.3}", RESTAURANT), "latitude");
        assertRefused(args("{\"radius\":100,\"latitude\":48.8,\"longitude\":181}", RESTAURANT), "longitude");
        assertRefused(args("{\"radius\":100,\"latitude\":\"x\",\"longitude\":2.3}", RESTAURANT), "latitude");
        assertRefused(args("{\"radius\":100,\"latitude\":48.8}", RESTAURANT), "longitude");
    }

    @Test
    void aFilterNeedsAKeyAndExactlyOneOfValueOrRegex() {
        assertRefused(args(AROUND_OPERA, "{\"value\":\"restaurant\"}"), "key");
        assertRefused(args(AROUND_OPERA, "{\"key\":\"\",\"value\":\"x\"}"), "key");
        assertRefused(args(AROUND_OPERA, "{\"key\":\"amenity\"}"), "value or regex");
        assertRefused(args(AROUND_OPERA, "{\"key\":\"amenity\",\"value\":\"a\",\"regex\":\"b\"}"), "value or regex");
    }

    @Test
    void aSearchThatIsNotForRestaurantsIsRefusedSoThePlaceIsNotLookedUpWithTheTool() {
        String place = "{\"key\":\"tourism\",\"value\":\"museum\"}";

        assertRefused(args(AROUND_OPERA, place), "amenity");
        assertRefused(args(AROUND_OPERA, place), "restaurant");
        assertRefused(args(AROUND_OPERA, "{\"key\":\"cuisine\",\"value\":\"thai\"}"), "amenity");
    }

    @Test
    void theRestaurantFilterMustBeTheExactTagAmenityRestaurant() {
        assertRefused(args(AROUND_OPERA, "{\"key\":\"amenity\",\"value\":\"cafe\"}"), "amenity");
        assertRefused(args(AROUND_OPERA, "{\"key\":\"amenity\",\"regex\":\"^restaurant$\"}"), "amenity");
    }

    @Test
    void aFilterOnTheNameTagIsRefusedWhetherExactOrRegex() {
        assertRefused(args(AROUND_OPERA, RESTAURANT + ",{\"key\":\"name\",\"value\":\"Opéra\"}"), "name");
        assertRefused(args(AROUND_OPERA, RESTAURANT + ",{\"key\":\"name\",\"regex\":\"Opéra\"}"), "name");
        assertRefused(args(AROUND_OPERA, "{\"key\":\"name\",\"value\":\"Opéra\"}," + RESTAURANT), "cuisine");
    }

    @Test
    void otherKeysThatContainTheWordNameAreStillAllowed() {
        String brand = "{\"key\":\"brand\",\"value\":\"Name\"}";

        assertThat(builder.build(args(AROUND_OPERA, RESTAURANT + "," + brand))).contains("[\"brand\"=\"Name\"]");
    }

    @Test
    void anUnknownElementTypeIsRefused() {
        assertRefused("{\"types\":[\"area\"],\"around\":[" + AROUND_OPERA + "],\"tags\":[" + RESTAURANT + "]}",
                "area");
    }

    @Test
    void aControlCharacterInALiteralIsRefused() {
        assertRefused(args(AROUND_OPERA, "{\"key\":\"amenity\",\"value\":\"a\\nb\"}"), "control");
    }
}
