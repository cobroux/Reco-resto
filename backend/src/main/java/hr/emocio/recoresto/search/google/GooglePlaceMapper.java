package hr.emocio.recoresto.search.google;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.config.CardEnrichmentProperties;
import hr.emocio.recoresto.search.enrichment.WebsiteAddress;
import hr.emocio.recoresto.search.restaurant.Attribution;
import hr.emocio.recoresto.search.restaurant.DataProvider;
import hr.emocio.recoresto.search.restaurant.Restaurant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Makes a {@link GooglePlace} out of one place of a Google answer (spec 008 data-model.md "Restaurant"). A field
 * Google does not provide stays absent; nothing is invented (FR-008).
 */
@Component
public class GooglePlaceMapper {

    /** D21 (2): the address is shown without the country. */
    private static final String COUNTRY_SUFFIX = ", France";

    /** D5: Google price level to the format of feature 003; FREE, UNSPECIFIED and any other value give no price. */
    public static final Map<String, String> PRICE_LEVELS = Map.of(
            "PRICE_LEVEL_INEXPENSIVE", "€",
            "PRICE_LEVEL_MODERATE", "€€",
            "PRICE_LEVEL_EXPENSIVE", "€€€",
            "PRICE_LEVEL_VERY_EXPENSIVE", "€€€€");

    /** Characters of a place or photo identifier accepted by the backend photo address (research R5). */
    private static final String IDENTIFIER_CHARACTERS = "[A-Za-z0-9_-]+";

    /** A whole place or photo identifier accepted by the backend photo address. */
    public static final String IDENTIFIER = "^" + IDENTIFIER_CHARACTERS + "$";

    /** Google photo name {@code places/{placeId}/photos/{photoId}} whose two identifiers the backend can serve. */
    private static final Pattern PHOTO_NAME = Pattern.compile(
            "^places/(" + IDENTIFIER_CHARACTERS + ")/photos/(" + IDENTIFIER_CHARACTERS + ")$");

    /** Spec 007 PO-8: the ports of a website the backend accepts, also those of {@code Restaurant.website}. */
    private final List<Integer> websitePorts;

    public GooglePlaceMapper(CardEnrichmentProperties enrichment) {
        this.websitePorts = enrichment.allowedPorts();
    }

    /** D16 (a): address of a photo through the backend; it never holds the key nor a Google host. */
    static String photoUrl(String placeId, String photoId) {
        return "/api/photos/" + placeId + "/" + photoId;
    }

    /** The place, or empty when it has no name ({@code Restaurant.name} is required). */
    public Optional<GooglePlace> map(JsonNode place, List<Position> searchPoints) {
        String name = text(place.path("displayName").path("text"));
        if (name == null) {
            return Optional.empty();
        }
        String id = text(place.path("id"));
        List<String> types = types(place.path("types"));
        JsonNode vegetarianNode = place.path("servesVegetarianFood");
        Boolean vegetarian = vegetarianNode.isBoolean() ? vegetarianNode.asBoolean() : null;
        Position location = location(place.path("location"));
        Integer distance = location == null ? null : nearestDistance(location, searchPoints);
        JsonNode photo = place.path("photos").path(0);
        List<String> diets = GooglePlaceTypes.statedDiets(types, vegetarian);
        Restaurant restaurant = new Restaurant(name, location, distance,
                text(place.path("primaryTypeDisplayName").path("text")), diets.isEmpty() ? null : diets, null,
                priceLevel(text(place.path("priceLevel"))),
                GoogleOpeningHours.parse(place.path("regularOpeningHours")), photoUrl(photo), null,
                address(place.path("shortFormattedAddress")), text(place.path("nationalPhoneNumber")), null,
                website(text(place.path("websiteUri"))), id, authors(photo.path("authorAttributions")),
                text(photo.path("googleMapsUri")), providers(place.path("attributions")));
        return Optional.of(new GooglePlace(id, types, text(place.path("primaryType")),
                text(place.path("businessStatus")), vegetarian, restaurant));
    }

    /** Spec 007 TC-3, research R3: {@code Restaurant.website} only when it passes the single website rule. */
    private String website(String value) {
        return WebsiteAddress.parse(value, websitePorts).isPresent() ? value : null;
    }

    private static String priceLevel(String googleLevel) {
        return googleLevel == null ? null : PRICE_LEVELS.get(googleLevel);
    }

    /** {@code Restaurant.photoUrl}: the backend address of the first photo, absent when it cannot be served. */
    private static String photoUrl(JsonNode photo) {
        String name = text(photo.path("name"));
        if (name == null) {
            return null;
        }
        Matcher matcher = PHOTO_NAME.matcher(name);
        return matcher.matches() ? photoUrl(matcher.group(1), matcher.group(2)) : null;
    }

    private static List<Attribution> authors(JsonNode authors) {
        List<Attribution> attributions = new ArrayList<>();
        authors.forEach(author -> {
            String displayName = text(author.path("displayName"));
            if (displayName != null) {
                attributions.add(new Attribution(displayName, text(author.path("uri"))));
            }
        });
        return attributions.isEmpty() ? null : attributions;
    }

    private static List<DataProvider> providers(JsonNode attributions) {
        List<DataProvider> providers = new ArrayList<>();
        attributions.forEach(attribution -> {
            String provider = text(attribution.path("provider"));
            if (provider != null) {
                providers.add(new DataProvider(provider, text(attribution.path("providerUri"))));
            }
        });
        return providers.isEmpty() ? null : providers;
    }

    /** {@code Restaurant.location}: null when missing or not a valid coordinate. */
    private static Position location(JsonNode location) {
        if (!location.path("latitude").isNumber() || !location.path("longitude").isNumber()) {
            return null;
        }
        double latitude = location.path("latitude").asDouble();
        double longitude = location.path("longitude").asDouble();
        return Position.isValid(latitude, longitude) ? new Position(latitude, longitude) : null;
    }

    /** {@code Restaurant.distanceMeters}: to the nearest search point, rounded to the meter. */
    private static Integer nearestDistance(Position location, List<Position> searchPoints) {
        return searchPoints.stream().mapToDouble(location::distanceMetersTo).min().stream()
                .mapToObj(meters -> (int) Math.round(meters)).findFirst().orElse(null);
    }

    private static String address(JsonNode node) {
        String address = text(node);
        if (address != null && address.endsWith(COUNTRY_SUFFIX)) {
            address = address.substring(0, address.length() - COUNTRY_SUFFIX.length()).trim();
        }
        return address == null || address.isEmpty() ? null : address;
    }

    private static List<String> types(JsonNode node) {
        List<String> types = new ArrayList<>();
        node.forEach(type -> {
            if (type.isString()) {
                types.add(type.asString());
            }
        });
        return types;
    }

    private static String text(JsonNode node) {
        return node.isString() && !node.asString().isBlank() ? node.asString().trim() : null;
    }
}
