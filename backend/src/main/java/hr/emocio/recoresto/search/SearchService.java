package hr.emocio.recoresto.search;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.api.SearchRequest;
import hr.emocio.recoresto.search.config.SearchProperties;
import hr.emocio.recoresto.search.criteria.MomentResolver;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import hr.emocio.recoresto.search.google.GooglePlace;
import hr.emocio.recoresto.search.google.GooglePlaceMapper;
import hr.emocio.recoresto.search.google.GooglePlacesClient;
import hr.emocio.recoresto.search.llm.JsonAnswers;
import hr.emocio.recoresto.search.llm.LlmChatClient;
import hr.emocio.recoresto.search.restaurant.DeckSelector;
import hr.emocio.recoresto.search.restaurant.Restaurant;
import hr.emocio.recoresto.search.restaurant.RestaurantFilters;
import hr.emocio.recoresto.search.restaurant.SearchResult;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The language model drives the search by tool calling (prototype, no spec).
 * <ol>
 * <li>The model calls {@code search_restaurants} with the criteria it reads in the sentence.</li>
 * <li>The backend runs the tool: Google Text Search around the browser position (the model never gives a place),
 * then the strict filters (open, radius, diets, status, one per chain), and gives back compact candidates with short
 * ids ({@code r1}, {@code r2}...).</li>
 * <li>The model picks and ranks candidates by id, with a short reason; the backend builds the cards from the Google
 * data, so no restaurant can be invented.</li>
 * </ol>
 * At most {@link #MAX_ROUNDS} model requests and {@link #MAX_TOOL_CALLS} searches. Any failure of the model falls
 * back to the backend: search with the raw sentence if the model never searched, backend order if it never picked.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    static final int MAX_ROUNDS = 3;
    static final int MAX_TOOL_CALLS = 2;
    /** Candidates shown to the model: enough to choose from, small enough to keep the second request fast. */
    static final int MAX_CANDIDATES = 20;
    static final int MIN_RADIUS_METERS = 300;
    static final int MAX_RADIUS_METERS = 5000;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_REASON_LENGTH = 120;
    private static final String TOOL_NAME = "search_restaurants";

    private static final Map<String, String> PRICE_LEVELS = Map.of(
            "inexpensive", "PRICE_LEVEL_INEXPENSIVE",
            "moderate", "PRICE_LEVEL_MODERATE",
            "expensive", "PRICE_LEVEL_EXPENSIVE",
            "very_expensive", "PRICE_LEVEL_VERY_EXPENSIVE");
    private static final Set<String> DIETS = Set.of("vegetarian", "vegan", "halal");

    private static final DateTimeFormatter NOW_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm (EEEE)",
            Locale.ENGLISH);

    private static final String SYSTEM_PROMPT = """
            You help a user in France find a restaurant from a sentence written in French.
            Current local date and time: %s, time zone Europe/Paris.
            The system already knows the user's position: never ask for a place and never search for one.

            Step 1. Call the tool search_restaurants once with what the sentence asks for:
            - query: a short French text of what to eat, with the user's words (dish, cuisine, style), for example
              "pizza napolitaine", "ramen", "restaurant gastronomique"; "restaurant" when the sentence says nothing
              about food. Never put a place, a price, a moment or a distance in the query.
            - price_levels: only when the sentence talks about budget ("pas cher", "petit budget" = inexpensive;
              "moyen" = inexpensive and moderate; "chic", "gastronomique" = expensive and very_expensive).
            - open_at: "now", or the local date-time of the moment named (ce midi = 12:30, ce soir = 20:00, demain
              midi = tomorrow 12:30), never in the past.
            - radius_m only when a distance is given; walking_minutes only when a travel time is given.
            - diets: only the diets the user requires.
            Step 2. If the tool finds no candidate, you may call it ONE more time with a broader search (simpler query,
            no price, larger radius).
            Step 3. Answer with one JSON object only, no other text:
            {"picks":[{"id":"r3","reason":"..."}]}
            - Rank at least 10 candidates (all of them when there are fewer), the best match for the sentence first.
              Only ids given by the tool. Leave out only a candidate that clearly does not match the sentence.
            - reason: one short French sentence (at most 12 words) saying why this place matches the request,
              based only on the data given by the tool.""";

    private static final List<Object> TOOLS = List.of(Map.of("type", "function", "function", Map.of(
            "name", TOOL_NAME,
            "description", "Searches restaurants around the user's position that are open at the given moment. "
                    + "Returns candidates with an id, name, cuisine, price, rating, distance and summary.",
            "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "query", Map.of("type", "string",
                                    "description", "Short French text of what to eat, or \"restaurant\""),
                            "price_levels", Map.of("type", "array", "items", Map.of("type", "string", "enum",
                                    List.of("inexpensive", "moderate", "expensive", "very_expensive"))),
                            "open_at", Map.of("type", "string",
                                    "description", "\"now\" or a local date-time such as 2026-11-28T12:30"),
                            "radius_m", Map.of("type", "integer", "description", "Search radius in meters"),
                            "walking_minutes", Map.of("type", "integer", "description", "Travel time on foot"),
                            "diets", Map.of("type", "array", "items", Map.of("type", "string", "enum",
                                    List.of("vegetarian", "vegan", "halal")))),
                    "required", List.of("query")))));

    /** One run of the tool: what was searched, and the candidates in backend order. */
    private record ToolRun(SearchCriteria criteria, List<Restaurant> candidates, List<Restaurant> zone,
            Map<String, JsonNode> rawById) {
    }

    private final LlmChatClient llm;
    private final GooglePlacesClient google;
    private final GooglePlaceMapper placeMapper;
    private final RestaurantFilters filters;
    private final DeckSelector deckSelector;
    private final MomentResolver momentResolver;
    private final SearchProperties properties;
    private final JsonMapper mapper;
    private final Clock clock;

    public SearchService(LlmChatClient llm, GooglePlacesClient google, GooglePlaceMapper placeMapper,
            RestaurantFilters filters, DeckSelector deckSelector, MomentResolver momentResolver,
            SearchProperties properties, JsonMapper mapper, Clock clock) {
        this.llm = llm;
        this.google = google;
        this.placeMapper = placeMapper;
        this.filters = filters;
        this.deckSelector = deckSelector;
        this.momentResolver = momentResolver;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
    }

    public SearchResult search(SearchRequest request, boolean includeZone) {
        Position position = request.position();
        if (position == null) {
            throw new SearchException(SearchErrorCode.PLACE_REQUIRED, "The browser position is required");
        }
        long start = System.nanoTime();
        log.info("Agent search received ({} characters)", request.sentence().length());
        List<Object> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt()));
        messages.add(Map.of("role", "user", "content", request.sentence()));

        ToolRun last = null;
        int toolCalls = 0;
        for (int round = 1; round <= MAX_ROUNDS; round++) {
            long roundStart = System.nanoTime();
            JsonNode message;
            try {
                message = llm.chat(messages, TOOLS);
            } catch (SearchException e) {
                log.warn("Agent round {} failed, falling back to the backend", round);
                break;
            }
            log.info("Agent round {}: model answered in {} ms", round, millis(roundStart));
            JsonNode calls = message.path("tool_calls");
            if (calls.isArray() && !calls.isEmpty()) {
                messages.add(message);
                for (JsonNode call : calls) {
                    String arguments = call.path("function").path("arguments").asString();
                    String result;
                    if (toolCalls < MAX_TOOL_CALLS && TOOL_NAME.equals(call.path("function").path("name").asString())) {
                        toolCalls++;
                        log.info("Agent tool call {}: {}", toolCalls, arguments);
                        last = runTool(arguments, position);
                        result = forModel(last);
                    } else {
                        result = "{\"error\":\"No more searches: give your final JSON answer now.\"}";
                    }
                    messages.add(Map.of("role", "tool", "tool_call_id", call.path("id").asString(), "content",
                            result));
                }
                continue;
            }
            if (last == null) {
                log.warn("Agent round {}: the model answered without searching", round);
                break;
            }
            SearchResult result = result(last, picks(message.path("content").asString(), last), includeZone);
            log.info("Agent search done in {} ms ({} model rounds, {} searches)", millis(start), round, toolCalls);
            return result;
        }
        if (last == null) {
            log.info("Agent fallback: search with the raw sentence");
            last = runTool(mapper.writeValueAsString(Map.of("query", request.sentence())), position);
        }
        log.info("Agent fallback: backend order");
        SearchResult result = result(last, List.of(), includeZone);
        log.info("Agent search done in {} ms (fallback)", millis(start));
        return result;
    }

    private String systemPrompt() {
        return SYSTEM_PROMPT.formatted(ZonedDateTime.now(clock.withZone(MomentResolver.ZONE)).format(NOW_FORMAT));
    }

    /** Runs the tool with checked arguments: anything invalid is dropped or clamped, never sent as is. */
    private ToolRun runTool(String argumentsJson, Position position) {
        long start = System.nanoTime();
        JsonNode args = JsonAnswers.firstObject(mapper, argumentsJson).orElse(mapper.createObjectNode());

        String query = text(args.path("query"));
        query = query == null ? "restaurant" : query.substring(0, Math.min(query.length(), MAX_QUERY_LENGTH));

        List<String> googlePrices = new ArrayList<>();
        args.path("price_levels").forEach(level -> {
            String google = PRICE_LEVELS.get(level.asString().toLowerCase(Locale.ROOT));
            if (google != null && !googlePrices.contains(google)) {
                googlePrices.add(google);
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
        radius = Math.clamp(radius, MIN_RADIUS_METERS, MAX_RADIUS_METERS);

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
        DeckSelector.Selection selection = deckSelector.select(List.of(kept), MAX_CANDIDATES);
        log.info("Agent tool: query of {} chars, prices {}, diets {}, radius {} m, moment {} -> Google {}, kept {}, "
                + "candidates {} in {} ms", query.length(), googlePrices, diets, Math.round(radius), moment,
                raw.size(), kept.size(), selection.deck().size(), millis(start));
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

    /** The model's picks as candidate indexes with their reason, in the model's order; unknown ids are ignored. */
    private List<Map.Entry<Integer, String>> picks(String content, ToolRun run) {
        log.debug("Agent final answer: {}", content);
        JsonNode answer = JsonAnswers.firstObject(mapper, content).orElse(null);
        if (answer == null || !answer.path("picks").isArray()) {
            log.warn("Agent final answer unreadable: backend order used");
            return List.of();
        }
        Set<Integer> seen = new LinkedHashSet<>();
        List<Map.Entry<Integer, String>> picks = new ArrayList<>();
        for (JsonNode pick : answer.path("picks")) {
            String id = text(pick.path("id"));
            if (id == null || !id.matches("r\\d{1,3}")) {
                continue;
            }
            int index = Integer.parseInt(id.substring(1)) - 1;
            if (index < 0 || index >= run.candidates().size() || !seen.add(index)) {
                continue;
            }
            String reason = text(pick.path("reason"));
            if (reason != null && reason.length() > MAX_REASON_LENGTH) {
                reason = reason.substring(0, MAX_REASON_LENGTH);
            }
            picks.add(Map.entry(index, reason == null ? "" : reason));
        }
        log.info("Agent picks: {} of {} candidates", picks.size(), run.candidates().size());
        return picks;
    }

    private SearchResult result(ToolRun run, List<Map.Entry<Integer, String>> picks, boolean includeZone) {
        // The model's picks first, with their reason; then the other valid candidates in backend order, so that the
        // deck is full whatever the number of picks.
        List<Restaurant> deck = new ArrayList<>();
        Set<Integer> used = new LinkedHashSet<>();
        for (Map.Entry<Integer, String> pick : picks) {
            if (deck.size() >= properties.deckSize()) {
                break;
            }
            Restaurant restaurant = run.candidates().get(pick.getKey());
            deck.add(pick.getValue().isBlank() ? restaurant : restaurant.withReason(pick.getValue()));
            used.add(pick.getKey());
        }
        for (int i = 0; i < run.candidates().size() && deck.size() < properties.deckSize(); i++) {
            if (!used.contains(i)) {
                deck.add(run.candidates().get(i));
            }
        }
        if (deck.isEmpty()) {
            throw new SearchException(SearchErrorCode.NO_RESULT, "No restaurant matches the search");
        }
        return new SearchResult(run.criteria(), deck, includeZone ? run.zone() : null);
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null && !(value instanceof List<?> list && list.isEmpty())) {
            map.put(key, value);
        }
    }

    private static String text(JsonNode node) {
        return node.isString() && !node.asString().isBlank() ? node.asString().trim() : null;
    }

    private static long millis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
