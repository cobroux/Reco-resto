package hr.emocio.recoresto.search.restaurant;

import hr.emocio.recoresto.search.config.SearchProperties;
import hr.emocio.recoresto.search.restaurant.OverpassClient.OverpassRefusal;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the JSON arguments given by the language model into an Overpass QL query, so that the model never writes
 * Overpass code itself. The JSON follows the tag filters of the Overpass documentation (criteria/per_tag):
 * <pre>
 * {"types":["node","way","relation"],                      // optional, default: all (nwr)
 *  "around":[{"radius":2500,"latitude":48.87,"longitude":2.33}],   // meters; one statement per point
 *  "tags":[{"key":"amenity","value":"restaurant"},                 // key="value"
 *          {"key":"cuisine","regex":"^(thai|vietnamese)$"}]}       // key~"regex"; filters are ANDed
 * </pre>
 * A filter on the {@code name} tag is refused (the model used it for places and words of the sentence).
 * The filter {@code amenity=restaurant} is required: the tool is for restaurants only, a place is never looked up with
 * it (the model gives the coordinates of the places in its answer).
 * A problem in the arguments is an {@link OverpassRefusal}: its message is handed back to the model.
 */
class OverpassQueryBuilder {

    private static final Set<String> TYPES = Set.of("node", "way", "relation");

    private final JsonMapper mapper;
    private final int timeoutSeconds;
    private final SearchProperties searchProperties;

    OverpassQueryBuilder(JsonMapper mapper, int timeoutSeconds, SearchProperties searchProperties) {
        this.mapper = mapper;
        this.timeoutSeconds = timeoutSeconds;
        this.searchProperties = searchProperties;
    }

    String build(String argumentsJson) {
        JsonNode arguments;
        try {
            arguments = mapper.readTree(argumentsJson);
        } catch (JacksonException e) {
            throw new OverpassRefusal("The arguments must be a JSON object (see the tool description).");
        }
        List<String> types = types(arguments.path("types"));
        String filters = filters(arguments.path("tags"));
        List<String> points = points(arguments.path("around"));

        List<String> statements = new ArrayList<>();
        for (String point : points) {
            for (String type : types) {
                statements.add(type + filters + point + ";");
            }
        }
        String header = "[out:json][timeout:" + timeoutSeconds + "];";
        return statements.size() == 1 ? header + statements.get(0) + "out center;"
                : header + "(" + String.join("", statements) + ");out center;";
    }

    private static List<String> types(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            return List.of("nwr");
        }
        List<String> types = new ArrayList<>();
        for (JsonNode type : node) {
            if (!type.isString() || !TYPES.contains(type.asString())) {
                throw new OverpassRefusal("Unknown element type " + type + "; allowed: node, way, relation.");
            }
            types.add(type.asString());
        }
        return types;
    }

    private static String filters(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            throw new OverpassRefusal("\"tags\" must be a non-empty list of filters, for example "
                    + "{\"key\":\"amenity\",\"value\":\"restaurant\"}.");
        }
        StringBuilder filters = new StringBuilder();
        boolean forRestaurants = false;
        for (JsonNode filter : node) {
            String key = text(filter.path("key"));
            if (key == null || key.isEmpty()) {
                throw new OverpassRefusal("Every tag filter needs a non-empty \"key\".");
            }
            if (key.equals("name")) {
                throw new OverpassRefusal("A filter on the name tag is not allowed: the name of a place or the words "
                        + "of the sentence are not restaurant names. Filter by type of cuisine with the cuisine tag, "
                        + "and give places as coordinates.");
            }
            String value = text(filter.path("value"));
            String regex = text(filter.path("regex"));
            if ((value == null) == (regex == null)) {
                throw new OverpassRefusal("Tag filter \"" + key + "\" needs exactly one of value or regex.");
            }
            forRestaurants |= key.equals("amenity") && "restaurant".equals(value);
            filters.append('[').append(literal(key)).append(value != null ? "=" : "~")
                    .append(literal(value != null ? value : regex)).append(']');
        }
        if (!forRestaurants) {
            throw new OverpassRefusal("This tool only finds restaurants: the search must include the tag filter "
                    + "{\"key\":\"amenity\",\"value\":\"restaurant\"}. Do not use it to look up a place: give the "
                    + "coordinates of places in your final answer.");
        }
        return filters.toString();
    }

    private List<String> points(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            throw new OverpassRefusal("\"around\" must be a non-empty list of points {radius (meters), latitude, "
                    + "longitude}.");
        }
        List<String> points = new ArrayList<>();
        for (JsonNode point : node) {
            long meters = meters(point);
            double latitude = coordinate(point.path("latitude"), "latitude", 90);
            double longitude = coordinate(point.path("longitude"), "longitude", 180);
            points.add("(around:" + meters + "," + plain(latitude) + "," + plain(longitude) + ")");
        }
        return points;
    }

    /** The radius of a point: given in meters, or as a time on foot that the backend turns into meters. */
    private long meters(JsonNode point) {
        JsonNode radius = point.path("radius");
        JsonNode minutes = point.path("walkingMinutes");
        if (radius.isMissingNode() == minutes.isMissingNode()) {
            throw new OverpassRefusal("Every point needs exactly one of radius or walkingMinutes (meters, or minutes on foot).");
        }
        if (!radius.isMissingNode()) {
            if (!radius.isNumber() || Math.round(radius.asDouble()) < 1) {
                throw new OverpassRefusal("Every point needs a \"radius\" of at least 1 meter.");
            }
            return Math.round(radius.asDouble());
        }
        long meters = minutes.isNumber() && minutes.asDouble() > 0 ? searchProperties.walkingMeters(minutes.asDouble())
                : 0;
        if (meters < 1) {
            throw new OverpassRefusal("\"walkingMinutes\" must be a positive number of minutes.");
        }
        return meters;
    }

    private static double coordinate(JsonNode node, String name, int limit) {
        if (!node.isNumber() || Math.abs(node.asDouble()) > limit) {
            throw new OverpassRefusal("Every point needs a numeric \"" + name + "\" between -" + limit + " and "
                    + limit + ".");
        }
        return node.asDouble();
    }

    private static String plain(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String text(JsonNode node) {
        return node.isString() ? node.asString() : null;
    }

    /** A quoted Overpass literal: backslashes and quotation marks are escaped with a backslash. */
    private static String literal(String text) {
        if (text.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
            throw new OverpassRefusal("A control character is not allowed in a key, a value or a regex.");
        }
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
