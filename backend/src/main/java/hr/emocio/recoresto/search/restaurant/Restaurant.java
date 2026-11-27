package hr.emocio.recoresto.search.restaurant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import hr.emocio.recoresto.search.api.Position;
import java.util.List;
import java.util.Map;

/**
 * A restaurant; every field is optional because the source may not provide it. {@code openingHours} is keyed by
 * upper-case English day name ("MONDAY"). {@code location} and {@code distanceMeters} are computed by the backend,
 * never taken from the language model. {@code website}, {@code placeId}, {@code photoAttributions},
 * {@code photoSourceUri} and {@code attributions} are optional additions of spec 008 (FR-011).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Restaurant(String name, Position location, Integer distanceMeters, String cuisine,
        List<String> diets, List<String> keywords, String priceLevel,
        Map<String, List<OpeningPeriod>> openingHours, String photoUrl, String signatureDish, String address,
        String phone, List<MenuItem> menu, String website, String placeId, List<Attribution> photoAttributions,
        String photoSourceUri, List<DataProvider> attributions, String reason) {

    /** Without {@code reason} (prototype agent search: why the model picked the restaurant). */
    public Restaurant(String name, Position location, Integer distanceMeters, String cuisine, List<String> diets,
            List<String> keywords, String priceLevel, Map<String, List<OpeningPeriod>> openingHours, String photoUrl,
            String signatureDish, String address, String phone, List<MenuItem> menu, String website, String placeId,
            List<Attribution> photoAttributions, String photoSourceUri, List<DataProvider> attributions) {
        this(name, location, distanceMeters, cuisine, diets, keywords, priceLevel, openingHours, photoUrl,
                signatureDish, address, phone, menu, website, placeId, photoAttributions, photoSourceUri, attributions,
                null);
    }

    public Restaurant withLocation(Position newLocation, Integer newDistanceMeters) {
        return new Restaurant(name, newLocation, newDistanceMeters, cuisine, diets, keywords, priceLevel,
                openingHours, photoUrl, signatureDish, address, phone, menu, website, placeId, photoAttributions,
                photoSourceUri, attributions, reason);
    }

    public Restaurant withReason(String newReason) {
        return new Restaurant(name, location, distanceMeters, cuisine, diets, keywords, priceLevel, openingHours,
                photoUrl, signatureDish, address, phone, menu, website, placeId, photoAttributions, photoSourceUri,
                attributions, newReason);
    }
}
