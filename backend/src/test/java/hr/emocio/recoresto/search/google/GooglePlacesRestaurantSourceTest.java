package hr.emocio.recoresto.search.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.criteria.CriteriaNode;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Spec 008 T1, T3, T4, T7, T45; implementation decision Q1. */
class GooglePlacesRestaurantSourceTest {

    private static final Position OPERA = new Position(48.8708, 2.3322);
    private static final Position CHATELET = new Position(48.8584, 2.3470);

    private final JsonMapper json = JsonMapper.builder().build();
    private final GooglePlacesClient client = mock(GooglePlacesClient.class);
    private final GooglePlacesRestaurantSource source = new GooglePlacesRestaurantSource(client,
            new GooglePlaceMapper());

    private JsonNode place(String id, double latitude, double longitude) {
        return json.readTree("{\"id\":\"" + id + "\",\"displayName\":{\"text\":\"" + id + "\"},\"location\":"
                + "{\"latitude\":" + latitude + ",\"longitude\":" + longitude + "},\"types\":[\"restaurant\"]}");
    }

    private static CriteriaNode cuisine(String value) {
        return new CriteriaNode(null, "cuisine", value, null);
    }

    private static CriteriaNode diet(String value) {
        return new CriteriaNode(null, "diet", value, null);
    }

    private static SearchCriteria tree(CriteriaNode combination) {
        return new SearchCriteria(null, null, combination, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private List<List<String>> includedTypesSent(int requests) {
        ArgumentCaptor<List<String>> types = ArgumentCaptor.forClass(List.class);
        verify(client, times(requests)).searchNearby(types.capture(), any(), anyDouble());
        return types.getAllValues();
    }

    @Test
    void oneSearchPerSearchPointWithTheRadiusGiven() {
        when(client.searchNearby(anyList(), eq(OPERA), eq(800.0))).thenReturn(List.of(place("a", 48.871, 2.332)));
        when(client.searchNearby(anyList(), eq(CHATELET), eq(800.0))).thenReturn(List.of(place("b", 48.858, 2.347)));

        List<List<GooglePlace>> perPoint = source.find(tree(null), List.of(OPERA, CHATELET), 800);

        assertThat(perPoint).hasSize(2);
        assertThat(perPoint.get(0)).extracting(GooglePlace::id).containsExactly("a");
        assertThat(perPoint.get(1)).extracting(GooglePlace::id).containsExactly("b");
        assertThat(perPoint.get(1).get(0).restaurant().placeId()).isEqualTo("b"); // T1
    }

    /** Q1: no cuisine, only "restaurant" (the D6 filter is applied by the backend). */
    @Test
    void withoutCuisineOnlyTheRestaurantTypeIsSent() {
        source.find(tree(null), List.of(OPERA), 2500);

        assertThat(includedTypesSent(1)).containsExactly(List.of("restaurant"));
    }

    @Test
    void theCuisineTypesOfTheCriteriaAreSent() {
        source.find(tree(new CriteriaNode("OR", null, null, List.of(cuisine("thai_restaurant"),
                cuisine("chinese_restaurant")))), List.of(OPERA), 2500);

        assertThat(includedTypesSent(1)).containsExactly(List.of("thai_restaurant", "chinese_restaurant"));
    }

    /** Q2: lists without a tree, OR between the cuisines. */
    @Test
    void theCuisineListsAreSentWhenThereIsNoTree() {
        source.find(new SearchCriteria(List.of("italian_restaurant", "pizza_restaurant"), List.of("vegetarian"), null,
                null, null, null, null), List.of(OPERA), 2500);

        assertThat(includedTypesSent(1)).containsExactly(List.of("italian_restaurant", "pizza_restaurant"));
    }

    /** Q1: an OR branch with a diet can be satisfied by any restaurant. */
    @Test
    void anOrBranchHoldingADietSendsOnlyTheRestaurantType() {
        source.find(tree(new CriteriaNode("OR", null, null, List.of(cuisine("asian_restaurant"),
                diet("vegetarian")))), List.of(OPERA), 2500);

        assertThat(includedTypesSent(1)).containsExactly(List.of("restaurant"));
    }

    @Test
    void aDietAndedWithACuisineKeepsTheCuisineTypes() {
        source.find(tree(new CriteriaNode("AND", null, null, List.of(cuisine("asian_restaurant"),
                diet("vegetarian")))), List.of(OPERA), 2500);

        assertThat(includedTypesSent(1)).containsExactly(List.of("asian_restaurant"));
    }

    /** Q1, research R1: at most 50 included types per request; more are split. */
    @Test
    void moreThanFiftyCuisineTypesAreSplitIntoRequestsOfAtMostFifty() {
        List<String> cuisines = GooglePlaceTypes.CUISINE_TYPES.subList(0, 60);
        when(client.searchNearby(anyList(), any(), anyDouble())).thenAnswer(call -> {
            List<String> types = call.getArgument(0);
            return types.contains(cuisines.get(0)) ? List.of(place("a", 48.871, 2.332), place("b", 48.871, 2.332))
                    : List.of(place("b", 48.871, 2.332), place("c", 48.871, 2.332));
        });

        List<List<GooglePlace>> perPoint = source.find(new SearchCriteria(cuisines, null, null, null, null, null,
                null), List.of(OPERA), 2500);

        List<List<String>> sent = includedTypesSent(2);
        assertThat(sent).allSatisfy(types -> assertThat(types).hasSizeLessThanOrEqualTo(50));
        List<String> all = new ArrayList<>();
        sent.forEach(all::addAll);
        assertThat(all).containsExactlyInAnyOrderElementsOf(cuisines);
        // the same place returned by two requests of one point is kept once
        assertThat(perPoint.get(0)).extracting(GooglePlace::id).containsExactly("a", "b", "c");
    }

    /** FR-017, T45: never more places than Google returned. */
    @Test
    void neverMorePlacesThanGoogleReturned() {
        List<JsonNode> twenty = IntStream.range(0, 20).mapToObj(i -> place("p" + i, 48.871, 2.332)).toList();
        when(client.searchNearby(anyList(), any(), anyDouble())).thenReturn(twenty);

        assertThat(source.find(tree(null), List.of(OPERA), 2500).get(0)).hasSize(20);
    }

    @Test
    void theDistanceIsTheOneToTheNearestOfAllSearchPoints() {
        when(client.searchNearby(anyList(), eq(OPERA), anyDouble())).thenReturn(List.of(place("a", 48.8590, 2.3475)));

        GooglePlace found = source.find(tree(null), List.of(OPERA, CHATELET), 2500).get(0).get(0);

        assertThat(found.restaurant().distanceMeters()).isEqualTo((int) Math.round(
                CHATELET.distanceMetersTo(new Position(48.8590, 2.3475))));
    }

    @Test
    void aGoogleFailureIsNotHidden() {
        when(client.searchNearby(anyList(), any(), anyDouble()))
                .thenThrow(new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "down"));

        assertThatThrownBy(() -> source.find(tree(null), List.of(OPERA, CHATELET), 2500))
                .isInstanceOfSatisfying(SearchException.class,
                        e -> assertThat(e.code()).isEqualTo(SearchErrorCode.SERVICE_UNAVAILABLE));
    }
}
