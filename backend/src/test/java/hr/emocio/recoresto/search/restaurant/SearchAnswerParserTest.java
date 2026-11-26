package hr.emocio.recoresto.search.restaurant;

import static org.assertj.core.api.Assertions.assertThat;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.criteria.CriteriaNode;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SearchAnswerParserTest {

    private final SearchAnswerParser parser = new SearchAnswerParser(JsonMapper.builder().build());

    private static final String OPERA = "{\"name\":\"Opéra, Paris\",\"latitude\":48.8708,\"longitude\":2.3322}";

    private static String understood(String criteria, String centers, String restaurants) {
        return "{\"understood\":true,\"criteria\":" + criteria + ",\"centers\":" + centers + ",\"restaurants\":"
                + restaurants + "}";
    }

    @Test
    void scenario1AsianOrVegetarianAroundOperaNow() {
        String criteria = """
                {"cuisines":["asian"],"diets":["vegetarian"],
                 "combination":{"operator":"OR","children":[
                   {"kind":"cuisine","value":"asian"},{"kind":"diet","value":"vegetarian"}]},
                 "places":["Opéra, Paris"],"moment":"now"}""";

        SearchAnswer answer = parser.parse(understood(criteria, "[" + OPERA + "]", "[]"));

        assertThat(answer.state()).isEqualTo(SearchAnswer.State.UNDERSTOOD);
        assertThat(answer.criteria().cuisines()).containsExactly("asian");
        assertThat(answer.criteria().diets()).containsExactly("vegetarian");
        assertThat(answer.criteria().combination().operator()).isEqualTo("OR");
        assertThat(answer.criteria().places()).containsExactly("Opéra, Paris");
        assertThat(answer.criteria().moment()).isEqualTo("now");
        assertThat(answer.places()).containsExactly(new NamedPlace("Opéra, Paris", new Position(48.8708, 2.3322)));
    }

    @Test
    void scenario2AndCombinationIsKept() {
        String criteria = """
                {"cuisines":["asian"],"diets":["vegetarian"],
                 "combination":{"operator":"AND","children":[
                   {"kind":"cuisine","value":"asian"},{"kind":"diet","value":"vegetarian"}]},
                 "places":["Opéra"],"moment":"now"}""";

        SearchCriteria parsed = parser.parse(understood(criteria, "[" + OPERA + "]", "[]")).criteria();

        assertThat(parsed.combination().operator()).isEqualTo("AND");
    }

    @Test
    void mixedGroupingIsKeptAsATree() {
        String criteria = """
                {"cuisines":["asian","italian"],"diets":["vegetarian"],
                 "combination":{"operator":"AND","children":[
                   {"operator":"OR","children":[{"kind":"cuisine","value":"asian"},{"kind":"cuisine","value":"italian"}]},
                   {"kind":"diet","value":"vegetarian"}]},
                 "places":["Opéra"],"moment":"now"}""";

        CriteriaNode tree = parser.parse(understood(criteria, "[" + OPERA + "]", "[]")).criteria().combination();

        assertThat(tree.operator()).isEqualTo("AND");
        assertThat(tree.children().get(0).operator()).isEqualTo("OR");
        assertThat(tree.children().get(0).children()).hasSize(2);
        assertThat(tree.children().get(1).value()).isEqualTo("vegetarian");
    }

    @Test
    void budgetAndSpecificDishAreNotPartOfTheCriteria() {
        String criteria = "{\"cuisines\":[\"japanese\"],\"budget\":\"cheap\",\"dish\":\"ramen\",\"places\":[\"Opéra\"]}";

        SearchCriteria parsed = parser.parse(understood(criteria, "[" + OPERA + "]", "[]")).criteria();

        assertThat(parsed).isEqualTo(new SearchCriteria(List.of("japanese"), List.of(), null, List.of("Opéra"), null,
                null, null));
    }

    @Test
    void aDistanceInTheSentenceIsKept() {
        String criteria = "{\"cuisines\":[\"thai\"],\"places\":[\"Opéra\"],\"distanceLimitMeters\":800}";

        assertThat(parser.parse(understood(criteria, "[" + OPERA + "]", "[]")).criteria().distanceLimitMeters())
                .isEqualTo(800);
    }

    @Test
    void aTravelTimeInTheSentenceIsKeptInMinutes() {
        String criteria = "{\"cuisines\":[\"thai\"],\"places\":[\"Opéra\"],\"walkingMinutes\":10}";

        assertThat(parser.parse(understood(criteria, "[" + OPERA + "]", "[]")).criteria().walkingMinutes())
                .isEqualTo(10);
    }

    @Test
    void notUnderstoodReplyGivesNotARequest() {
        SearchAnswer answer = parser.parse("{\"understood\":false}");

        assertThat(answer.state()).isEqualTo(SearchAnswer.State.NOT_A_REQUEST);
    }

    @Test
    void replyThatIsNotJsonIsUnreadableNeverNotARequest() {
        assertThat(parser.parse("blablabla").state()).isEqualTo(SearchAnswer.State.UNREADABLE);
    }

    @Test
    void understoodWithoutCriteriaHasEmptyCriteria() {
        SearchAnswer answer = parser.parse("{\"understood\":true,\"restaurants\":[]}");

        assertThat(answer.state()).isEqualTo(SearchAnswer.State.UNDERSTOOD);
        assertThat(answer.criteria()).isEqualTo(new SearchCriteria(null, null, null, null, null, null, null));
    }

    @Test
    void jsonSurroundedByTextIsStillRead() {
        String reply = "Here it is: " + understood("{\"cuisines\":[\"thai\"]}", "[" + OPERA + "]", "[]") + " Enjoy";

        assertThat(parser.parse(reply).state()).isEqualTo(SearchAnswer.State.UNDERSTOOD);
    }

    @Test
    void placeOutsideFranceIsReported() {
        SearchAnswer answer = parser.parse("{\"understood\":true,\"outsideFrance\":true}");

        assertThat(answer.state()).isEqualTo(SearchAnswer.State.OUTSIDE_FRANCE);
    }

    @Test
    void aCenterWithAnInvalidCoordinateIsANamedPlaceWithoutPosition() {
        String centers = "[{\"name\":\"A\",\"latitude\":148.0,\"longitude\":2.3},"
                + "{\"name\":\"B\",\"latitude\":\"x\",\"longitude\":2.3},{\"name\":\"C\",\"latitude\":48.87},"
                + "{\"latitude\":148.0,\"longitude\":2.3},"
                + "{\"name\":\"D\",\"latitude\":48.8708,\"longitude\":2.3322}]";

        SearchAnswer answer = parser.parse(understood("{\"cuisines\":[\"thai\"]}", centers, "[]"));

        assertThat(answer.places()).containsExactly(new NamedPlace("A", null), new NamedPlace("B", null),
                new NamedPlace("C", null), new NamedPlace("D", new Position(48.8708, 2.3322)));
    }

    @Test
    void withoutCentersTheNamedPlacesOfTheCriteriaHaveNoPosition() {
        SearchAnswer answer = parser.parse(understood("{\"places\":[\"Opéra, Paris\",\"Châtelet\"]}", "[]",
                "[]"));

        assertThat(answer.places()).containsExactly(new NamedPlace("Opéra, Paris", null),
                new NamedPlace("Châtelet", null));
    }

    @Test
    void noPlaceAtAllGivesNoPlace() {
        assertThat(parser.parse(understood("{\"cuisines\":[\"thai\"]}", "[]", "[]")).places()).isEmpty();
    }

    @Test
    void restaurantsWrittenByTheModelAreIgnoredBecauseTheBackendFindsThem() {
        String restaurants = "[{\"name\":\"Invented\",\"address\":\"1 rue A\"}]";

        SearchAnswer answer = parser.parse(understood("{\"cuisines\":[\"thai\"]}", "[" + OPERA + "]", restaurants));

        assertThat(answer.state()).isEqualTo(SearchAnswer.State.UNDERSTOOD);
        assertThat(answer.places()).containsExactly(new NamedPlace("Opéra, Paris", new Position(48.8708, 2.3322)));
    }

    // --- Robust reading (T043: T12-T19; FR-004 to FR-007; D12) ---

    private static String recorded(String name) {
        try (var stream = SearchAnswerParserTest.class.getResourceAsStream("/llm-answers/" + name)) {
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static final NamedPlace OPERA_PLACE = new NamedPlace("Opéra, Paris", new Position(48.8708, 2.3322));

    /** T12. */
    @Test
    void textAroundTheAnswerIsIgnored() {
        SearchAnswer answer = parser.parse(recorded("prose-around.txt"));

        assertThat(answer.state()).isEqualTo(SearchAnswer.State.UNDERSTOOD);
        assertThat(answer.places()).containsExactly(OPERA_PLACE);
    }

    /** T13. */
    @Test
    void aJsonCodeFenceIsIgnored() {
        SearchAnswer answer = parser.parse(recorded("json-fence.txt"));

        assertThat(answer.state()).isEqualTo(SearchAnswer.State.UNDERSTOOD);
        assertThat(answer.places()).containsExactly(OPERA_PLACE);
    }

    /** T14, D12: the first complete object is read. */
    @Test
    void ofTwoObjectsTheFirstCompleteOneIsRead() {
        SearchAnswer answer = parser.parse(recorded("two-objects.txt"));

        assertThat(answer.state()).isEqualTo(SearchAnswer.State.UNDERSTOOD);
        assertThat(answer.places()).containsExactly(OPERA_PLACE);
    }

    @Test
    void aBalancedTextThatIsNotJsonIsSkippedForTheNextObject() {
        SearchAnswer answer = parser.parse("{voici} " + understood("{}", "[" + OPERA + "]", "[]"));

        assertThat(answer.state()).isEqualTo(SearchAnswer.State.UNDERSTOOD);
    }

    /** T15, FR-005: missing optional fields give empty criteria (002 defaults). */
    @Test
    void onlyUnderstoodAndAPlaceGiveEmptyCriteria() {
        SearchAnswer answer = parser.parse(recorded("only-understood-and-place.txt"));

        assertThat(answer.state()).isEqualTo(SearchAnswer.State.UNDERSTOOD);
        assertThat(answer.criteria()).isEqualTo(new SearchCriteria(null, null, null, null, null, null, null));
        assertThat(answer.places()).containsExactly(OPERA_PLACE);
    }

    /** T16. */
    @Test
    void withoutCentersThePlaceIsKeptWithoutPosition() {
        SearchAnswer answer = parser.parse(recorded("no-centers.txt"));

        assertThat(answer.state()).isEqualTo(SearchAnswer.State.UNDERSTOOD);
        assertThat(answer.places()).containsExactly(new NamedPlace("Opéra, Paris", null));
    }

    /** T17. */
    @Test
    void aCenterOutOfRangeIsAPlaceWithoutPosition() {
        SearchAnswer answer = parser.parse(recorded("out-of-range-center.txt"));

        assertThat(answer.state()).isEqualTo(SearchAnswer.State.UNDERSTOOD);
        assertThat(answer.places()).containsExactly(new NamedPlace("Opéra, Paris", null));
    }

    /** T18, FR-007. */
    @Test
    void aMissingClosingBraceIsUnreadable() {
        assertThat(parser.parse(recorded("missing-closing-brace.txt")).state())
                .isEqualTo(SearchAnswer.State.UNREADABLE);
    }

    /** T19, FR-007. */
    @Test
    void anEmptyAnswerIsUnreadable() {
        assertThat(parser.parse(recorded("empty.txt")).state()).isEqualTo(SearchAnswer.State.UNREADABLE);
        assertThat(parser.parse("   ").state()).isEqualTo(SearchAnswer.State.UNREADABLE);
        assertThat(parser.parse(null).state()).isEqualTo(SearchAnswer.State.UNREADABLE);
    }

    @Test
    void bracesInsideStringsAreNotStructure() {
        String reply = "{\"understood\":true,\"criteria\":{\"places\":[\"Le {Bistrot} }\"]},"
                + "\"centers\":[{\"name\":\"Le \\\"{\\\" bar\",\"latitude\":48.8708,\"longitude\":2.3322}]} fin }";

        SearchAnswer answer = parser.parse(reply);

        assertThat(answer.state()).isEqualTo(SearchAnswer.State.UNDERSTOOD);
        assertThat(answer.criteria().places()).containsExactly("Le {Bistrot} }");
        assertThat(answer.places()).containsExactly(new NamedPlace("Le \"{\" bar", new Position(48.8708, 2.3322)));
    }

    /** FR-007: only a readable statement that the sentence is not a request is "not a request". */
    @Test
    void anObjectThatDoesNotSayWhetherItUnderstoodIsUnreadable() {
        assertThat(parser.parse("{\"criteria\":{\"places\":[\"Opéra\"]}}").state())
                .isEqualTo(SearchAnswer.State.UNREADABLE);
        assertThat(parser.parse("{\"understood\":\"maybe\"}").state()).isEqualTo(SearchAnswer.State.UNREADABLE);
    }

    @Test
    void criteriaThatCannotBeReadAreUnreadable() {
        assertThat(parser.parse("{\"understood\":true,\"criteria\":\"italian\"}").state())
                .isEqualTo(SearchAnswer.State.UNREADABLE);
        assertThat(parser.parse("{\"understood\":true,\"criteria\":{\"cuisines\":{\"a\":1}}}").state())
                .isEqualTo(SearchAnswer.State.UNREADABLE);
    }
}
