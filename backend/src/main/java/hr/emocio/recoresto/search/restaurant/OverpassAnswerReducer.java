package hr.emocio.recoresto.search.restaurant;

import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Shrinks an Overpass answer to what a restaurant is made of (the input of {@link OsmRestaurantMapper}); the raw
 * answer is far longer. It keeps, for each element that has a name, its type, id and coordinates and the tags a
 * restaurant of the answer is made of (name, cuisine, diets, address, phone, opening hours, image: the fields of the restaurant model,
 * spec 002 FR-012). The choice of tags is not validated by the team yet (plan.md O13).
 */
class OverpassAnswerReducer {

    private static final Set<String> TAGS = Set.of("name", "cuisine", "phone", "contact:phone", "opening_hours",
            "image");
    private static final List<String> TAG_PREFIXES = List.of("diet:", "addr:");

    private final JsonMapper mapper;

    OverpassAnswerReducer(JsonMapper mapper) {
        this.mapper = mapper;
    }

    ObjectNode reduce(JsonNode answer) {
        ObjectNode reduced = mapper.createObjectNode();
        ArrayNode elements = reduced.putArray("elements");
        for (JsonNode element : answer.path("elements")) {
            ObjectNode tags = keptTags(element.path("tags"));
            if (!tags.path("name").isString() || tags.path("name").asString().isBlank()) {
                continue;
            }
            ObjectNode copy = elements.addObject();
            copy.put("type", element.path("type").asString());
            copy.put("id", element.path("id").asLong());
            copyCoordinates(element, copy);
            if (element.path("center").isObject()) {
                copyCoordinates(element.path("center"), copy.putObject("center"));
            }
            copy.set("tags", tags);
        }
        return reduced;
    }

    private static void copyCoordinates(JsonNode from, ObjectNode to) {
        if (from.path("lat").isNumber() && from.path("lon").isNumber()) {
            to.put("lat", from.path("lat").asDouble());
            to.put("lon", from.path("lon").asDouble());
        }
    }

    private ObjectNode keptTags(JsonNode tags) {
        ObjectNode kept = mapper.createObjectNode();
        for (String key : tags.propertyNames()) {
            if (TAGS.contains(key) || TAG_PREFIXES.stream().anyMatch(key::startsWith)) {
                kept.set(key, tags.get(key));
            }
        }
        return kept;
    }
}
