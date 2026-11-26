package hr.emocio.recoresto.search.restaurant;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the final answer of the language model into a {@link SearchAnswer}: its state, what it understood (criteria)
 * and the places it named, with their coordinates checked (spec 008 data-model.md "Model answer"). Any other field the
 * model writes (restaurants, queries, filters) is ignored: the backend builds the search itself (FR-002).
 */
@Component
public class SearchAnswerParser {

    private final JsonMapper mapper;

    public SearchAnswerParser(JsonMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Reads the first complete JSON object of the answer (D12), whatever text or code fence surrounds it (FR-004).
     * Missing optional fields give empty criteria (FR-005). An answer without a readable object, or one that does not
     * say whether it understood, is {@code UNREADABLE}, never "not a request" (FR-007).
     */
    public SearchAnswer parse(String reply) {
        Optional<JsonNode> found = firstObject(reply);
        if (found.isEmpty()) {
            return SearchAnswer.unreadable();
        }
        JsonNode root = found.get();
        Optional<Boolean> understood = bool(root.path("understood"));
        if (understood.isEmpty()) {
            return SearchAnswer.unreadable();
        }
        if (!understood.get()) {
            return SearchAnswer.notARequest();
        }
        if (bool(root.path("outsideFrance")).orElse(false)) {
            return SearchAnswer.placeOutsideFrance();
        }
        JsonNode criteriaNode = root.path("criteria");
        SearchCriteria criteria;
        if (criteriaNode.isMissingNode() || criteriaNode.isNull()) {
            criteria = new SearchCriteria(null, null, null, null, null, null, null);
        } else if (!criteriaNode.isObject()) {
            return SearchAnswer.unreadable();
        } else {
            try {
                criteria = mapper.treeToValue(criteriaNode, SearchCriteria.class);
            } catch (JacksonException e) {
                return SearchAnswer.unreadable();
            }
        }
        return new SearchAnswer(SearchAnswer.State.UNDERSTOOD, criteria, places(root.path("centers"), criteria));
    }

    /** A boolean, or the text "true" / "false"; empty for anything else. */
    private static Optional<Boolean> bool(JsonNode node) {
        if (node.isBoolean()) {
            return Optional.of(node.asBoolean());
        }
        if (node.isString() && (node.asString().equalsIgnoreCase("true") || node.asString().equalsIgnoreCase("false"))) {
            return Optional.of(Boolean.parseBoolean(node.asString()));
        }
        return Optional.empty();
    }

    /**
     * The first balanced {@code {...}} block of the text that reads as a JSON object; braces inside strings are not
     * structure. A block that does not read as JSON is skipped. Code fences are text around the object.
     */
    private Optional<JsonNode> firstObject(String reply) {
        if (reply == null) {
            return Optional.empty();
        }
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < reply.length(); i++) {
            char c = reply.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = depth > 0;
            } else if (c == '{') {
                if (depth++ == 0) {
                    start = i;
                }
            } else if (c == '}' && depth > 0 && --depth == 0) {
                Optional<JsonNode> object = readObject(reply.substring(start, i + 1));
                if (object.isPresent()) {
                    return object;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<JsonNode> readObject(String text) {
        try {
            JsonNode node = mapper.readTree(text);
            return node != null && node.isObject() ? Optional.of(node) : Optional.empty();
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    /**
     * One place per center of the answer (a center without a valid position keeps its name, without position); when
     * the answer gives no center, the places named in the criteria, without position.
     */
    private static List<NamedPlace> places(JsonNode centers, SearchCriteria criteria) {
        List<NamedPlace> places = new ArrayList<>();
        for (JsonNode center : centers) {
            String name = center.path("name").isString() && !center.path("name").asString().isBlank()
                    ? center.path("name").asString().trim() : null;
            Position position = positionOf(center);
            if (name != null || position != null) {
                places.add(new NamedPlace(name, position));
            }
        }
        if (places.isEmpty()) {
            criteria.places().stream().filter(name -> name != null && !name.isBlank())
                    .forEach(name -> places.add(new NamedPlace(name.trim(), null)));
        }
        return places;
    }

    private static Position positionOf(JsonNode node) {
        JsonNode latitude = node.path("latitude");
        JsonNode longitude = node.path("longitude");
        if (!latitude.isNumber() || !longitude.isNumber()) {
            return null;
        }
        return Position.isValid(latitude.asDouble(), longitude.asDouble())
                ? new Position(latitude.asDouble(), longitude.asDouble()) : null;
    }
}
