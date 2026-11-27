package hr.emocio.recoresto.search.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.config.ChainProperties;
import hr.emocio.recoresto.search.config.SearchProperties;
import hr.emocio.recoresto.search.config.TestCardEnrichmentProperties;
import hr.emocio.recoresto.search.config.ToolSearchProperties;
import hr.emocio.recoresto.search.criteria.CriteriaMatcher;
import hr.emocio.recoresto.search.criteria.MomentResolver;
import hr.emocio.recoresto.search.google.GooglePlaceMapper;
import hr.emocio.recoresto.search.google.GooglePlacesClient;
import hr.emocio.recoresto.search.restaurant.DeckSelector;
import hr.emocio.recoresto.search.restaurant.RestaurantFilters;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The restaurant search tool on its own (spec 009 FR-002). The whole search, with the model, stays covered by
 * {@code SearchServiceTest}; here only what the tool itself decides.
 */
class SearchRestaurantsToolTest {

    private static final Position HERE = new Position(48.8700, 2.3300);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-26T11:00:00Z"), ZoneOffset.UTC);

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final GooglePlacesClient google = mock(GooglePlacesClient.class);
    private final SearchRestaurantsTool tool = new SearchRestaurantsTool(google,
            new GooglePlaceMapper(TestCardEnrichmentProperties.defaults()),
            new RestaurantFilters(new CriteriaMatcher()), new DeckSelector(new ChainProperties(null, null)),
            new MomentResolver(CLOCK), new SearchProperties(2.5, 4, 5),
            new ToolSearchProperties(300, 5000, 100, 120, 20, 3, 2), mapper);

    private void googleFinds(int count) {
        List<JsonNode> places = IntStream.rangeClosed(1, count).mapToObj(i -> mapper.readTree("""
                {"id":"p%d","displayName":{"text":"Resto %d"},"primaryType":"restaurant",
                 "location":{"latitude":%s,"longitude":2.33},"shortFormattedAddress":"%d rue X"}"""
                .formatted(i, i, 48.8700 + i * 0.0009, i))).toList();
        when(google.searchText(anyString(), any(), anyDouble(), anyList())).thenReturn(places);
    }

    @Test
    void theToolIsNamedAndLimitedByTheSearchLimit() {
        assertThat(tool.name()).isEqualTo("search_restaurants");
        assertThat(tool.callLimit()).isEqualTo(2);
        assertThat(tool.definition().toString()).contains("search_restaurants", "query");
    }

    @Test
    void theArgumentsAreCheckedAndTheModelReadsCompactCandidatesWithShortIds() {
        googleFinds(2);

        String result = tool.execute("{\"radius_m\":50,\"diets\":[\"kosher\"]}", new ToolContext(HERE));

        // no query: any restaurant; radius below 300 m: 300 m; unknown diet dropped
        verify(google).searchText("restaurant", HERE, 300.0, List.of());
        JsonNode answer = mapper.readTree(result);
        assertThat(answer.path("count").asInt()).isEqualTo(2);
        assertThat(answer.path("candidates").path(0).path("id").asString()).isEqualTo("r1");
        assertThat(answer.path("candidates").path(0).path("name").asString()).isEqualTo("Resto 1");
        assertThat(answer.path("candidates").path(0).has("address")).isFalse();
    }

    @Test
    void theRunIsKeptInTheContextForTheNextTools() {
        googleFinds(2);
        ToolContext context = new ToolContext(HERE);

        tool.execute("{\"query\":\"pizza\"}", context);

        assertThat(context.lastRun().candidates()).hasSize(2);
        assertThat(context.lastRun().criteria().query()).isEqualTo("pizza");
    }

    /** PR #18 review: an empty second search must not discard the candidates of the first one. */
    @Test
    void anEmptyRunDoesNotReplaceARunThatHasCandidates() {
        googleFinds(2);
        when(google.searchText(eq("b"), any(), anyDouble(), anyList())).thenReturn(new ArrayList<>());
        ToolContext context = new ToolContext(HERE);

        tool.execute("{\"query\":\"a\"}", context);
        tool.execute("{\"query\":\"b\"}", context);

        assertThat(context.lastRun().candidates()).hasSize(2);
        assertThat(context.lastRun().criteria().query()).isEqualTo("a");
    }

    @Test
    void anEmptyFirstRunIsKeptSoThatTheSearchEndsWithNoResult() {
        when(google.searchText(anyString(), any(), anyDouble(), anyList())).thenReturn(new ArrayList<>());
        ToolContext context = new ToolContext(HERE);

        tool.execute("{\"query\":\"a\"}", context);

        assertThat(context.lastRun()).isNotNull();
        assertThat(context.lastRun().candidates()).isEmpty();
    }
}
