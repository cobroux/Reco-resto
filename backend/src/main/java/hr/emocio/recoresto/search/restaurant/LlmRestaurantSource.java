package hr.emocio.recoresto.search.restaurant;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import hr.emocio.recoresto.search.llm.LlmChatClient;
import hr.emocio.recoresto.search.place.ApiAdresseClient;
import hr.emocio.recoresto.search.place.ResolvedPlace;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks the language model for restaurants. Location and distance are recomputed from the geocoded address; what the
 * model does not provide stays absent. Web access for the model is not wired yet (see plan.md O10).
 */
@Component
public class LlmRestaurantSource implements RestaurantSource {

    private static final DateTimeFormatter MOMENT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private static final String INSTRUCTIONS = """
            You suggest real restaurants in France that match search criteria. Reply with one JSON object only, no
            other text: {"restaurants":[...]}. Each restaurant has these fields, each omitted when unknown (never
            invent a value): "name", "address" (full postal address), "cuisine", "diets" (list), "keywords" (list),
            "priceLevel", "openingHours" (object keyed by upper-case English day name such as "MONDAY", each value a
            list of {"opens":"HH:mm","closes":"HH:mm"}), "photoUrl", "signatureDish" (a dish one can eat there),
            "phone", "menu" (list of {"name","description","price"}).
            Only suggest restaurants within the given radius of the given place and open at the given moment.""";

    private final LlmChatClient llm;
    private final ApiAdresseClient geocoder;
    private final JsonMapper mapper;

    public LlmRestaurantSource(LlmChatClient llm, ApiAdresseClient geocoder, JsonMapper mapper) {
        this.llm = llm;
        this.geocoder = geocoder;
        this.mapper = mapper;
    }

    @Override
    public List<Restaurant> find(SearchCriteria criteria, ResolvedPlace center, double radiusMeters,
            ZonedDateTime moment) {
        String reply = llm.complete(INSTRUCTIONS, """
                Place: %s
                Radius in meters: %d
                Moment: %s
                Criteria: %s""".formatted(center.name(), (long) radiusMeters, moment.format(MOMENT_FORMAT),
                mapper.writeValueAsString(criteria)));
        List<Restaurant> restaurants = new ArrayList<>();
        try {
            JsonNode items = mapper.readTree(jsonObjectIn(reply)).path("restaurants");
            for (JsonNode item : items) {
                Restaurant parsed = mapper.treeToValue(item, Restaurant.class);
                if (parsed.name() != null && !parsed.name().isBlank()) {
                    restaurants.add(locate(parsed, center.position()));
                }
            }
        } catch (JacksonException e) {
            // An answer that cannot be read is treated as "no restaurant".
        }
        return restaurants;
    }

    private Restaurant locate(Restaurant restaurant, Position center) {
        Optional<Position> location = restaurant.address() == null || restaurant.address().isBlank()
                ? Optional.empty()
                : geocoder.geocode(restaurant.address());
        return restaurant.withLocation(location.orElse(null),
                location.map(position -> (int) Math.round(center.distanceMetersTo(position))).orElse(null));
    }

    private static String jsonObjectIn(String reply) {
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        return start >= 0 && end > start ? reply.substring(start, end + 1) : reply;
    }
}
