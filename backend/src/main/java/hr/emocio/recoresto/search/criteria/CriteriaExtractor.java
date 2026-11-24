package hr.emocio.recoresto.search.criteria;

import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.llm.LlmChatClient;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Asks the LLM to turn the user's sentence into {@link SearchCriteria}. */
@Component
public class CriteriaExtractor {

    private static final DateTimeFormatter NOW_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private static final String INSTRUCTIONS = """
            You turn a French sentence in which someone says what they want to eat into search criteria.
            Reply with one JSON object only, no other text, with these fields:
            - "understood": false if the sentence cannot be understood or is not about finding a restaurant, else true.
            - "cuisines": types of cuisine asked for, as short lowercase English terms (e.g. "asian").
            - "diets": diets asked for, as short lowercase English terms (e.g. "vegetarian").
            - "combination": the AND/OR grouping of cuisines and diets exactly as the user expressed it. A node is
              either {"operator":"AND"|"OR","children":[nodes]} or a leaf {"kind":"cuisine"|"diet","value":"..."}.
            - "places": every place named (neighborhood, landmark, street, city) as a list of strings, each with its
              city when known (e.g. "Opéra, Paris").
            - "moment": "now" when no moment is given, else the ISO-8601 local date-time targeted (yyyy-MM-ddTHH:mm).
            - "distanceLimitMeters": a maximum distance in meters when the user gives one (convert a walking time
              to meters), else null.
            Ignore budget and specific dishes: they are not search criteria.
            Current date and time in Paris: %s (%s).""";

    private static final Logger log = LoggerFactory.getLogger(CriteriaExtractor.class);

    private final LlmChatClient llm;
    private final JsonMapper mapper;
    private final Clock clock;

    public CriteriaExtractor(LlmChatClient llm, JsonMapper mapper, Clock clock) {
        this.llm = llm;
        this.mapper = mapper;
        this.clock = clock;
    }

    public SearchCriteria extract(String sentence) {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(MomentResolver.ZONE));
        String reply = llm.complete(INSTRUCTIONS.formatted(now.format(NOW_FORMAT), now.getDayOfWeek()), sentence);
        try {
            JsonNode node = mapper.readTree(jsonObjectIn(reply));
            if (!node.isObject() || node.path("understood").asBoolean(true) == false) {
                throw notUnderstood(null);
            }
            ((ObjectNode) node).set("places", placeNames(node.path("places")));
            return mapper.treeToValue(node, SearchCriteria.class);
        } catch (JacksonException e) {
            log.warn("The criteria given by the language model cannot be read: {}", e.getOriginalMessage());
            throw notUnderstood(e);
        }
    }

    /** The model may give a place as a string or as an object with a "name" and a "city": keep "name, city". */
    private ArrayNode placeNames(JsonNode places) {
        ArrayNode names = mapper.createArrayNode();
        for (JsonNode place : places) {
            if (place.isString()) {
                names.add(place.asString());
            } else if (place.path("name").isString()) {
                String city = place.path("city").isString() ? place.path("city").asString() : "";
                names.add(city.isBlank() ? place.path("name").asString()
                        : place.path("name").asString() + ", " + city);
            }
        }
        return names;
    }

    /** The model may wrap the JSON in a code fence or add text around it: keep the outermost braces. */
    private static String jsonObjectIn(String reply) {
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        return start >= 0 && end > start ? reply.substring(start, end + 1) : reply;
    }

    private static SearchException notUnderstood(Throwable cause) {
        return new SearchException(SearchErrorCode.NOT_UNDERSTOOD, "The sentence cannot be understood", cause);
    }
}
