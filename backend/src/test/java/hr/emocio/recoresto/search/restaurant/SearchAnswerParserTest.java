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

        assertThat(answer.understood()).isTrue();
        assertThat(answer.outsideFrance()).isFalse();
        assertThat(answer.criteria().cuisines()).containsExactly("asian");
        assertThat(answer.criteria().diets()).containsExactly("vegetarian");
        assertThat(answer.criteria().combination().operator()).isEqualTo("OR");
        assertThat(answer.criteria().places()).containsExactly("Opéra, Paris");
        assertThat(answer.criteria().moment()).isEqualTo("now");
        assertThat(answer.centers()).containsExactly(new Position(48.8708, 2.3322));
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
    void notUnderstoodReplyGivesNotUnderstood() {
        SearchAnswer answer = parser.parse("{\"understood\":false}");

        assertThat(answer.understood()).isFalse();
    }

    @Test
    void replyThatIsNotJsonIsNotUnderstood() {
        assertThat(parser.parse("blablabla").understood()).isFalse();
    }

    @Test
    void understoodWithoutCriteriaIsNotUnderstood() {
        assertThat(parser.parse("{\"understood\":true,\"restaurants\":[]}").understood()).isFalse();
    }

    @Test
    void jsonSurroundedByTextIsStillRead() {
        String reply = "Here it is: " + understood("{\"cuisines\":[\"thai\"]}", "[" + OPERA + "]", "[]") + " Enjoy";

        assertThat(parser.parse(reply).understood()).isTrue();
    }

    @Test
    void placeOutsideFranceIsReported() {
        SearchAnswer answer = parser.parse("{\"understood\":true,\"outsideFrance\":true}");

        assertThat(answer.understood()).isTrue();
        assertThat(answer.outsideFrance()).isTrue();
    }

    @Test
    void centerWithAnInvalidCoordinateIsDropped() {
        String centers = "[{\"latitude\":148.0,\"longitude\":2.3},{\"latitude\":\"x\",\"longitude\":2.3},"
                + "{\"latitude\":48.87},{\"latitude\":48.8708,\"longitude\":2.3322}]";

        SearchAnswer answer = parser.parse(understood("{\"cuisines\":[\"thai\"]}", centers, "[]"));

        assertThat(answer.centers()).containsExactly(new Position(48.8708, 2.3322));
    }

    @Test
    void noCenterIsKeptEmptyForTheCallerToDecide() {
        assertThat(parser.parse(understood("{\"cuisines\":[\"thai\"]}", "[]", "[]")).centers()).isEmpty();
    }

    @Test
    void restaurantsWrittenByTheModelAreIgnoredBecauseTheBackendReadsThemFromOverpass() {
        String restaurants = "[{\"name\":\"Invented\",\"address\":\"1 rue A\"}]";

        SearchAnswer answer = parser.parse(understood("{\"cuisines\":[\"thai\"]}", "[" + OPERA + "]", restaurants));

        assertThat(answer.understood()).isTrue();
        assertThat(answer.restaurants()).isEmpty();
    }
}
