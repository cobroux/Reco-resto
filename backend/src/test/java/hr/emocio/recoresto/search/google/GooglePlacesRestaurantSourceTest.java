package hr.emocio.recoresto.search.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.config.TestCardEnrichmentProperties;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Spec 008 T1, T45; Text Search experiment of 2026-11-26 (one text request per search point). */
class GooglePlacesRestaurantSourceTest {

    private static final Position OPERA = new Position(48.8708, 2.3322);
    private static final Position CHATELET = new Position(48.8584, 2.3470);

    private final JsonMapper json = JsonMapper.builder().build();
    private final GooglePlacesClient client = mock(GooglePlacesClient.class);
    private final GooglePlacesRestaurantSource source = new GooglePlacesRestaurantSource(client,
            new GooglePlaceMapper(TestCardEnrichmentProperties.defaults()));

    private JsonNode place(String id, double latitude, double longitude) {
        return json.readTree("{\"id\":\"" + id + "\",\"displayName\":{\"text\":\"" + id + "\"},\"location\":"
                + "{\"latitude\":" + latitude + ",\"longitude\":" + longitude + "},\"types\":[\"restaurant\"]}");
    }

    private static SearchCriteria query(String query) {
        return new SearchCriteria(query, null, null, null, null, null);
    }

    private static SearchCriteria noQuery() {
        return query(null);
    }

    private List<String> queriesSent(int requests) {
        ArgumentCaptor<String> queries = ArgumentCaptor.forClass(String.class);
        verify(client, times(requests)).searchText(queries.capture(), any(), anyDouble());
        return queries.getAllValues();
    }

    @Test
    void oneSearchPerSearchPointWithTheRadiusGiven() {
        when(client.searchText(anyString(), eq(OPERA), eq(800.0))).thenReturn(List.of(place("a", 48.871, 2.332)));
        when(client.searchText(anyString(), eq(CHATELET), eq(800.0))).thenReturn(List.of(place("b", 48.858, 2.347)));

        List<List<GooglePlace>> perPoint = source.find(noQuery(), List.of(OPERA, CHATELET), 800);

        assertThat(perPoint).hasSize(2);
        assertThat(perPoint.get(0)).extracting(GooglePlace::id).containsExactly("a");
        assertThat(perPoint.get(1)).extracting(GooglePlace::id).containsExactly("b");
        assertThat(perPoint.get(1).get(0).restaurant().placeId()).isEqualTo("b"); // T1
        assertThat(queriesSent(2)).hasSize(2);
    }

    /** Text Search experiment: the text of the model is sent as it is, one request per point. */
    @Test
    void theQueryOfTheModelIsSentAsItIs() {
        source.find(query("frites"), List.of(OPERA), 2500);

        assertThat(queriesSent(1)).containsExactly("frites");
    }

    @Test
    void withoutQueryTheTextIsRestaurant() {
        source.find(noQuery(), List.of(OPERA), 2500);
        source.find(query("  "), List.of(OPERA), 2500);

        assertThat(queriesSent(2)).containsExactly("restaurant", "restaurant");
    }

    @Test
    void theQueryIsTrimmed() {
        source.find(query("  restaurant étoilé Michelin "), List.of(OPERA), 2500);

        assertThat(queriesSent(1)).containsExactly("restaurant étoilé Michelin");
    }

    /** FR-017, T45: never more places than Google returned. */
    @Test
    void neverMorePlacesThanGoogleReturned() {
        List<JsonNode> twenty = IntStream.range(0, 20).mapToObj(i -> place("p" + i, 48.871, 2.332)).toList();
        when(client.searchText(anyString(), any(), anyDouble())).thenReturn(twenty);

        assertThat(source.find(noQuery(), List.of(OPERA), 2500).get(0)).hasSize(20);
    }

    @Test
    void theDistanceIsTheOneToTheNearestOfAllSearchPoints() {
        when(client.searchText(anyString(), eq(OPERA), anyDouble())).thenReturn(List.of(place("a", 48.8590, 2.3475)));

        GooglePlace found = source.find(noQuery(), List.of(OPERA, CHATELET), 2500).get(0).get(0);

        assertThat(found.restaurant().distanceMeters()).isEqualTo((int) Math.round(
                CHATELET.distanceMetersTo(new Position(48.8590, 2.3475))));
    }

    @Test
    void aGoogleFailureIsNotHidden() {
        when(client.searchText(anyString(), any(), anyDouble()))
                .thenThrow(new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "down"));

        assertThatThrownBy(() -> source.find(noQuery(), List.of(OPERA, CHATELET), 2500))
                .isInstanceOfSatisfying(SearchException.class,
                        e -> assertThat(e.code()).isEqualTo(SearchErrorCode.SERVICE_UNAVAILABLE));
    }
}
