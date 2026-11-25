package hr.emocio.recoresto.search.restaurant;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the final answer of the language model into a {@link SearchAnswer}: what it understood (criteria) and the
 * search points, with their coordinates checked. The model gives no restaurant: any it writes is ignored, the backend
 * reads them from the Overpass answers ({@link OsmRestaurantMapper}).
 */
@Component
public class SearchAnswerParser {

    private final JsonMapper mapper;

    public SearchAnswerParser(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public SearchAnswer parse(String reply) {
        JsonNode root;
        try {
            root = mapper.readTree(jsonObjectIn(reply));
        } catch (JacksonException e) {
            return SearchAnswer.notUnderstood();
        }
        if (!root.path("understood").asBoolean(false)) {
            return SearchAnswer.notUnderstood();
        }
        if (root.path("outsideFrance").asBoolean(false)) {
            return SearchAnswer.placeOutsideFrance();
        }
        SearchCriteria criteria;
        try {
            criteria = root.path("criteria").isObject() ? mapper.treeToValue(root.path("criteria"),
                    SearchCriteria.class) : null;
        } catch (JacksonException e) {
            criteria = null;
        }
        if (criteria == null) {
            return SearchAnswer.notUnderstood();
        }
        List<Position> centers = new ArrayList<>();
        for (JsonNode node : root.path("centers")) {
            positionOf(node).ifPresent(centers::add);
        }
        return new SearchAnswer(true, false, criteria, centers, List.of());
    }

    private static java.util.Optional<Position> positionOf(JsonNode node) {
        JsonNode latitude = node.path("latitude");
        JsonNode longitude = node.path("longitude");
        if (!latitude.isNumber() || !longitude.isNumber()) {
            return java.util.Optional.empty();
        }
        Position position = new Position(latitude.asDouble(), longitude.asDouble());
        return isValid(position) ? java.util.Optional.of(position) : java.util.Optional.empty();
    }

    private static boolean isValid(Position position) {
        return position.latitude() >= -90 && position.latitude() <= 90 && position.longitude() >= -180
                && position.longitude() <= 180;
    }

    private static String jsonObjectIn(String reply) {
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        return start >= 0 && end > start ? reply.substring(start, end + 1) : reply;
    }
}
