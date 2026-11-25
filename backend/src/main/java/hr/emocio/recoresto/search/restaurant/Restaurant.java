package hr.emocio.recoresto.search.restaurant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import hr.emocio.recoresto.search.api.Position;
import java.util.List;
import java.util.Map;

/**
 * A restaurant; every field is optional because the source may not provide it. {@code openingHours} is keyed by
 * upper-case English day name ("MONDAY"). {@code location} and {@code distanceMeters} are computed by the backend,
 * never taken from the language model.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Restaurant(String name, Position location, Integer distanceMeters, String cuisine,
        List<String> diets, List<String> keywords, String priceLevel,
        Map<String, List<OpeningPeriod>> openingHours, String photoUrl, String signatureDish, String address,
        String phone, List<MenuItem> menu) {

    public Restaurant withLocation(Position newLocation, Integer newDistanceMeters) {
        return new Restaurant(name, newLocation, newDistanceMeters, cuisine, diets, keywords, priceLevel,
                openingHours, photoUrl, signatureDish, address, phone, menu);
    }
}
