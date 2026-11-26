package hr.emocio.recoresto.search.restaurant;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import hr.emocio.recoresto.search.llm.JsonAnswers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the final answer of the language model into a {@link SearchAnswer}: its state, what it understood (criteria)
 * and the places it named, with their coordinates checked (spec 008 data-model.md "Model answer"). Any other field the
 * model writes (restaurants, filters, Google types) is ignored: the backend builds the search itself (FR-002); the
 * only text sent to Google is the {@code query} of the criteria (experiment of 2026-11-26).
 */
@Component
public class SearchAnswerParser {

    private final JsonMapper mapper;

    public SearchAnswerParser(JsonMapper mapper) {
        this.mapper = mapper;
    }

    /** Criteria fields the model sometimes writes outside "criteria" (one brace too many closes it early). */
    private static final List<String> CRITERIA_FIELDS = List.of("query", "diets", "places", "moment",
            "distanceLimitMeters", "walkingMinutes");

    /**
     * "criteria" completed with the criteria fields found at the top level of the answer, when "criteria" does not
     * already give them (answer seen on 2026-11-26: "moment" closed out of "criteria"). Anything else is unchanged.
     */
    private JsonNode withTopLevelFields(JsonNode root) {
        JsonNode criteria = root.path("criteria");
        if (!(criteria.isMissingNode() || criteria.isNull() || criteria.isObject())) {
            return criteria;
        }
        List<String> outside = CRITERIA_FIELDS.stream().filter(root::has)
                .filter(field -> !criteria.isObject() || !criteria.has(field)).toList();
        if (outside.isEmpty()) {
            return criteria;
        }
        ObjectNode completed = criteria.isObject() ? ((ObjectNode) criteria).deepCopy() : mapper.createObjectNode();
        outside.forEach(field -> completed.set(field, root.get(field)));
        return completed;
    }

    /**
     * Reads the first complete JSON object of the answer (D12), whatever text or code fence surrounds it (FR-004).
     * Missing optional fields give empty criteria (FR-005). An answer without a readable object, or one that does not
     * say whether it understood, is {@code UNREADABLE}, never "not a request" (FR-007).
     */
    public SearchAnswer parse(String reply) {
        Optional<JsonNode> found = JsonAnswers.firstObject(mapper, reply);
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
        JsonNode criteriaNode = withTopLevelFields(root);
        SearchCriteria criteria;
        if (criteriaNode.isMissingNode() || criteriaNode.isNull()) {
            criteria = new SearchCriteria(null, null, null, null, null, null);
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
