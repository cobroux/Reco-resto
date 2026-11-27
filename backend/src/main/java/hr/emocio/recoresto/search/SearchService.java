package hr.emocio.recoresto.search;

import static hr.emocio.recoresto.search.llm.JsonAnswers.text;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.api.SearchRequest;
import hr.emocio.recoresto.search.config.SearchProperties;
import hr.emocio.recoresto.search.config.ToolSearchProperties;
import hr.emocio.recoresto.search.criteria.MomentResolver;
import hr.emocio.recoresto.search.llm.JsonAnswers;
import hr.emocio.recoresto.search.llm.LlmChatClient;
import hr.emocio.recoresto.search.restaurant.Restaurant;
import hr.emocio.recoresto.search.restaurant.SearchResult;
import hr.emocio.recoresto.search.tool.LlmTool;
import hr.emocio.recoresto.search.tool.SearchRestaurantsTool;
import hr.emocio.recoresto.search.tool.ToolContext;
import hr.emocio.recoresto.search.tool.ToolRegistry;
import hr.emocio.recoresto.search.tool.ToolRun;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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
 * The language model drives the search by tool calling (prototype, no spec; tools in a registry since spec 009).
 * <ol>
 * <li>The model calls {@code search_restaurants} with the criteria it reads in the sentence.</li>
 * <li>The backend runs the tool ({@link SearchRestaurantsTool}): Google Text Search around the browser position (the
 * model never gives a place), then the strict filters (open, radius, diets, status, one per chain), and gives back
 * compact candidates with short ids ({@code r1}, {@code r2}...).</li>
 * <li>The model picks and ranks candidates by id, with a short reason; the backend builds the cards from the Google
 * data, so no restaurant can be invented.</li>
 * </ol>
 * At most {@code search.tool.max-rounds} model requests, and for each tool the number of calls it allows
 * ({@link LlmTool#callLimit()}). A model that answers without searching did not take the sentence for a restaurant
 * request: {@code NOT_UNDERSTOOD}. Any failure of the model falls back to the backend: search with the raw sentence if
 * the model never searched, backend order if it never picked.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private static final DateTimeFormatter NOW_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm (EEEE)",
            Locale.ENGLISH);

    /** What the model reads when it asks for an unknown tool or for one more call than a tool allows. */
    private static final String NO_MORE_CALLS = "{\"error\":\"No more searches: give your final JSON answer now.\"}";

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

    private final LlmChatClient llm;
    private final ToolRegistry registry;
    private final SearchRestaurantsTool searchTool;
    private final SearchProperties properties;
    private final ToolSearchProperties limits;
    private final JsonMapper mapper;
    private final Clock clock;

    public SearchService(LlmChatClient llm, ToolRegistry registry, SearchRestaurantsTool searchTool,
            SearchProperties properties, ToolSearchProperties limits, JsonMapper mapper, Clock clock) {
        this.llm = llm;
        this.registry = registry;
        this.searchTool = searchTool;
        this.properties = properties;
        this.limits = limits;
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

        ToolContext context = new ToolContext(position);
        List<Object> tools = registry.definitions();
        Map<String, Integer> callsByTool = new HashMap<>();
        int toolCalls = 0;
        for (int round = 1; round <= limits.maxRounds(); round++) {
            long roundStart = System.nanoTime();
            JsonNode message;
            try {
                message = llm.chat(messages, tools);
            } catch (SearchException e) {
                log.warn("Agent round {} failed, falling back to the backend", round);
                break;
            }
            log.info("Agent round {}: model answered in {} ms", round, millis(roundStart));
            JsonNode calls = message.path("tool_calls");
            if (calls.isArray() && !calls.isEmpty()) {
                messages.add(message);
                for (JsonNode call : calls) {
                    String name = call.path("function").path("name").asString();
                    String arguments = call.path("function").path("arguments").asString();
                    LlmTool tool = registry.find(name).orElse(null);
                    String result;
                    if (tool != null && callsByTool.getOrDefault(name, 0) < tool.callLimit()) {
                        callsByTool.merge(name, 1, Integer::sum);
                        toolCalls++;
                        log.debug("Agent tool call {}: {}", toolCalls, arguments);
                        result = tool.execute(arguments, context);
                    } else {
                        result = NO_MORE_CALLS;
                    }
                    messages.add(Map.of("role", "tool", "tool_call_id", call.path("id").asString(), "content",
                            result));
                }
                continue;
            }
            if (context.lastRun() == null) {
                log.info("Agent round {}: the model answered without searching: not understood", round);
                throw new SearchException(SearchErrorCode.NOT_UNDERSTOOD, "The sentence is not a restaurant request");
            }
            SearchResult result = result(context.lastRun(), picks(message.path("content").asString(),
                    context.lastRun()), includeZone);
            log.info("Agent search done in {} ms ({} model rounds, {} tool calls)", millis(start), round, toolCalls);
            return result;
        }
        if (context.lastRun() == null) {
            log.info("Agent fallback: search with the raw sentence");
            context.record(searchTool.run(mapper.writeValueAsString(Map.of("query", request.sentence())), position));
        }
        log.info("Agent fallback: backend order");
        SearchResult result = result(context.lastRun(), List.of(), includeZone);
        log.info("Agent search done in {} ms (fallback)", millis(start));
        return result;
    }

    private String systemPrompt() {
        return SYSTEM_PROMPT.formatted(ZonedDateTime.now(clock.withZone(MomentResolver.ZONE)).format(NOW_FORMAT));
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
            if (reason != null && reason.length() > limits.maxReasonLength()) {
                reason = reason.substring(0, limits.maxReasonLength());
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

    private static long millis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
