package hr.emocio.recoresto.search.place;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.google.GooglePlacesClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Locates a place the model named without a usable position (spec 008 FR-006, D13) with Google Places Text Search. A
 * place that cannot be located is "place required"; a place located outside France is "outside France".
 */
@Component
public class PlaceLocator {

    /** Country code of France in Google {@code addressComponents} (ISO 3166-1, CLDR). */
    static final String FRANCE = "FR";

    private final GooglePlacesClient client;

    public PlaceLocator(GooglePlacesClient client) {
        this.client = client;
    }

    public Position locate(String name) {
        if (name == null || name.isBlank()) {
            throw placeRequired();
        }
        JsonNode place = client.locate(name.trim()).orElseThrow(PlaceLocator::placeRequired);
        Position position = position(place.path("location"));
        if (position == null) {
            throw placeRequired();
        }
        String country = country(place.path("addressComponents"));
        if (country != null && !FRANCE.equalsIgnoreCase(country)) {
            throw new SearchException(SearchErrorCode.OUTSIDE_FRANCE,
                    "Nappe n'est disponible qu'en France pour l'instant");
        }
        return position;
    }

    private static Position position(JsonNode location) {
        if (!location.path("latitude").isNumber() || !location.path("longitude").isNumber()) {
            return null;
        }
        double latitude = location.path("latitude").asDouble();
        double longitude = location.path("longitude").asDouble();
        return Position.isValid(latitude, longitude) ? new Position(latitude, longitude) : null;
    }

    /** The short country code of the place, or null when Google gives none. */
    private static String country(JsonNode components) {
        for (JsonNode component : components) {
            for (JsonNode type : component.path("types")) {
                if ("country".equals(type.asString()) && component.path("shortText").isString()) {
                    return component.path("shortText").asString();
                }
            }
        }
        return null;
    }

    private static SearchException placeRequired() {
        return new SearchException(SearchErrorCode.PLACE_REQUIRED, "A place is required");
    }
}
