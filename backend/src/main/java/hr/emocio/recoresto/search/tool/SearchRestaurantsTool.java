package hr.emocio.recoresto.search.tool;

import static hr.emocio.recoresto.search.llm.JsonAnswers.text;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.config.SearchProperties;
import hr.emocio.recoresto.search.config.ToolSearchProperties;
import hr.emocio.recoresto.search.criteria.MomentResolver;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import hr.emocio.recoresto.search.google.GooglePlace;
import hr.emocio.recoresto.search.google.GooglePlaceMapper;
import hr.emocio.recoresto.search.google.GooglePlaceTypes;
import hr.emocio.recoresto.search.google.GooglePlacesClient;
import hr.emocio.recoresto.search.llm.JsonAnswers;
import hr.emocio.recoresto.search.restaurant.DeckSelector;
import hr.emocio.recoresto.search.restaurant.Restaurant;
import hr.emocio.recoresto.search.restaurant.RestaurantFilters;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The tool {@code search_restaurants} (moved from {@code SearchService}, spec 009 FR-002, behavior unchanged): Google
 * Text Search around the browser position (the model never gives a place), then the strict filters (open, radius,
 * diets, status, one per chain), and compact candidates with short ids ({@code r1}, {@code r2}...) for the model.
 */
@Component
public class SearchRestaurantsTool implements LlmTool {

    public static final String NAME = "search_restaurants";

    private static final Logger log = LoggerFactory.getLogger(SearchRestaurantsTool.class);

    private static final String GOOGLE_PRICE_PREFIX = "PRICE_LEVEL_";
    /** Tool price names ("inexpensive"...) to Google levels, from the single table of {@link GooglePlaceMapper}. */
    private static final Map<String, String> PRICE_LEVELS = GooglePlaceMapper.PRICE_LEVELS.keySet().stream()
            .collect(Collectors.toMap(level -> level.substring(GOOGLE_PRICE_PREFIX.length()).toLowerCase(Locale.ROOT),
                    level -> level));
    /** The diets Google can state, from the single table of {@link GooglePlaceTypes}. */
    private static final Set<String> DIETS = GooglePlaceTypes.DIET_TYPES.keySet();

    private static final Map<String, Object> DEFINITION = Map.of("type", "function", "function", Map.of(
            "name", NAME,
            "description", "Searches restaurants around the user's position that are open at the given moment. "
                    + "Returns candidates with an id, name, cuisine, price, rating, distance and summary.",
            "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "query", Map.of("type", "string",
                                    "description", "Short French text of what to eat, or \"restaurant\""),
                            "price_levels", Map.of("type", "array", "items", Map.of("type", "string", "enum",
                                    sorted(PRICE_LEVELS.keySet()))),
                            "open_at", Map.of("type", "string",
                                    "description", "\"now\" or a local date-time such as 2026-11-28T12:30"),
                            "radius_m", Map.of("type", "integer", "description", "Search radius in meters"),
                            "walking_minutes", Map.of("type", "integer", "description", "Travel time on foot"),
                            "diets", Map.of("type", "array", "items", Map.of("type", "string", "enum",
                                    sorted(DIETS)))),
                    "required", List.of("query"))));

    private final GooglePlacesClient google;
    private final GooglePlaceMapper placeMapper;
    private final RestaurantFilters filters;
    private final DeckSelector deckSelector;
    private final MomentResolver momentResolver;
    private final SearchProperties properties;
    private final ToolSearchProperties limits;
    private final JsonMapper mapper;

    public SearchRestaurantsTool(GooglePlacesClient google, GooglePlaceMapper placeMapper, RestaurantFilters filters,
            DeckSelector deckSelector, MomentResolver momentResolver, SearchProperties properties,
            ToolSearchProperties limits, JsonMapper mapper) {
        this.google = google;
        this.placeMapper = placeMapper;
        this.filters = filters;
        this.deckSelector = deckSelector;
        this.momentResolver = momentResolver;
        this.properties = properties;
        this.limits = limits;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, Object> definition() {
        return DEFINITION;
    }

    @Override
    public int callLimit() {
        return limits.maxToolCalls();
    }

    @Override
    public String execute(String argumentsJson, ToolContext context) {
        ToolRun run = run(argumentsJson, context.position());
        context.record(run);
        return forModel(run);
    }

    /** Runs the tool with checked arguments: anything invalid is dropped or clamped, never sent as is. */
    public ToolRun run(String argumentsJson, Position position) {
        long start = System.nanoTime();
        JsonNode args = JsonAnswers.firstObject(mapper, argumentsJson).orElse(mapper.createObjectNode());

        String query = text(args.path("query"));
        query = query == null ? "restaurant" : query.substring(0, Math.min(query.length(), limits.maxQueryLength()));

        List<String> googlePrices = new ArrayList<>();
        args.path("price_levels").forEach(level -> {
            String googleLevel = PRICE_LEVELS.get(level.asString().toLowerCase(Locale.ROOT));
            if (googleLevel != null && !googlePrices.contains(googleLevel)) {
                googlePrices.add(googleLevel);
            }
        });

        List<String> diets = new ArrayList<>();
        args.path("diets").forEach(diet -> {
            String value = diet.asString().toLowerCase(Locale.ROOT);
            if (DIETS.contains(value) && !diets.contains(value)) {
                diets.add(value);
            }
        });

        double radius = properties.defaultRadiusKm() * 1000;
        if (args.path("radius_m").isNumber()) {
            radius = args.path("radius_m").asDouble();
        } else if (args.path("walking_minutes").isNumber()) {
            radius = properties.walkingMeters(args.path("walking_minutes").asDouble());
        }
        radius = Math.clamp(radius, limits.minRadiusMeters(), limits.maxRadiusMeters());

        String openAt = text(args.path("open_at"));
        ZonedDateTime moment;
        try {
            moment = momentResolver.resolve(openAt);
        } catch (SearchException unreadableOrPast) {
            log.info("Agent tool: moment {} ignored, now used", openAt);
            openAt = "now";
            moment = momentResolver.resolve(null);
        }

        SearchCriteria criteria = new SearchCriteria(query, diets, openAt == null ? "now" : openAt,
                (int) Math.round(radius));

        List<JsonNode> raw = google.searchText(query, position, radius, googlePrices);
        Map<String, JsonNode> rawById = new LinkedHashMap<>();
        Map<String, GooglePlace> places = new LinkedHashMap<>();
        for (JsonNode node : raw) {
            placeMapper.map(node, List.of(position)).ifPresent(place -> {
                if (place.id() != null && places.putIfAbsent(place.id(), place) == null) {
                    rawById.put(place.id(), node);
                }
            });
        }
        List<Restaurant> kept = filters.apply(List.copyOf(places.values()), criteria, radius, moment);
        DeckSelector.Selection selection = deckSelector.select(List.of(kept),
                Math.max(limits.maxCandidates(), properties.deckSize()));
        log.info("Agent tool: query of {} chars, prices {}, {} diets, radius {} m, moment {} -> Google {}, kept {}, "
                + "candidates {} in {} ms", query.length(), googlePrices, diets.size(), Math.round(radius), moment,
                raw.size(), kept.size(), selection.deck().size(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return new ToolRun(criteria, selection.deck(), selection.zone(), rawById);
    }

    /** The compact candidates the model reads: short ids, no address, no photo, no hours. */
    private String forModel(ToolRun run) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (int i = 0; i < run.candidates().size(); i++) {
            Restaurant restaurant = run.candidates().get(i);
            JsonNode raw = run.rawById().getOrDefault(restaurant.placeId(), mapper.createObjectNode());
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("id", "r" + (i + 1));
            candidate.put("name", restaurant.name());
            putIfPresent(candidate, "cuisine", restaurant.cuisine());
            putIfPresent(candidate, "price", restaurant.priceLevel());
            if (raw.path("rating").isNumber()) {
                candidate.put("rating", raw.path("rating").asDouble());
                candidate.put("reviews", raw.path("userRatingCount").asInt(0));
            }
            putIfPresent(candidate, "distance_m", restaurant.distanceMeters());
            putIfPresent(candidate, "diets", restaurant.diets());
            putIfPresent(candidate, "summary", text(raw.path("editorialSummary").path("text")));
            candidates.add(candidate);
        }
        return mapper.writeValueAsString(Map.of("count", candidates.size(), "candidates", candidates));
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null && !(value instanceof List<?> list && list.isEmpty())) {
            map.put(key, value);
        }
    }
}
