package hr.emocio.recoresto.search.restaurant;

import hr.emocio.recoresto.search.api.Position;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Makes restaurants out of OpenStreetMap elements (the reduced elements of {@link OverpassAnswerReducer}), so that
 * what the language model writes never reaches the deck. A field OpenStreetMap does not have stays absent. The choice
 * of tags is not validated by the team yet (plan.md O13).
 */
@Component
class OsmRestaurantMapper {

    List<Restaurant> map(Collection<JsonNode> elements, List<Position> centers) {
        List<Restaurant> restaurants = new ArrayList<>();
        for (JsonNode element : elements) {
            restaurantOf(element, centers).ifPresent(restaurants::add);
        }
        return restaurants;
    }

    private Optional<Restaurant> restaurantOf(JsonNode element, List<Position> centers) {
        JsonNode tags = element.path("tags");
        String name = text(tags.path("name"));
        if (name == null) {
            return Optional.empty();
        }
        Position location = location(element);
        Integer distance = location == null ? null : nearestDistance(location, centers);
        String phone = text(tags.path("phone"));
        String image = text(tags.path("image"));
        return Optional.of(new Restaurant(name, location, distance, text(tags.path("cuisine")), diets(tags), List.of(),
                null, OsmOpeningHours.parse(text(tags.path("opening_hours"))),
                image != null && (image.startsWith("http://") || image.startsWith("https://")) ? image : null, null,
                address(tags), phone != null ? phone : text(tags.path("contact:phone")), null));
    }

    /** The point of a node, or the center of a way or relation; null when missing or not a valid coordinate. */
    private static Position location(JsonNode element) {
        JsonNode point = element.path("lat").isNumber() ? element : element.path("center");
        if (!point.path("lat").isNumber() || !point.path("lon").isNumber()) {
            return null;
        }
        double latitude = point.path("lat").asDouble();
        double longitude = point.path("lon").asDouble();
        return Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180 ? new Position(latitude, longitude) : null;
    }

    private static Integer nearestDistance(Position location, List<Position> centers) {
        return centers.stream().mapToDouble(location::distanceMetersTo).min().stream()
                .mapToObj(meters -> (int) Math.round(meters)).findFirst().orElse(null);
    }

    /** "3 Rue X, 75002 Paris": needs at least the street, so that a route can be offered. */
    private static String address(JsonNode tags) {
        String street = text(tags.path("addr:street"));
        if (street == null) {
            return null;
        }
        String number = text(tags.path("addr:housenumber"));
        String city = join(text(tags.path("addr:postcode")), text(tags.path("addr:city")));
        String line = number == null ? street : number + " " + street;
        return city.isEmpty() ? line : line + ", " + city;
    }

    private static String join(String... parts) {
        List<String> present = new ArrayList<>();
        for (String part : parts) {
            if (part != null) {
                present.add(part);
            }
        }
        return String.join(" ", present);
    }

    /** The diets the restaurant is tagged {@code diet:<name>=yes} for, in alphabetical order. */
    private static List<String> diets(JsonNode tags) {
        TreeSet<String> diets = new TreeSet<>();
        for (String key : tags.propertyNames()) {
            if (key.startsWith("diet:") && "yes".equals(text(tags.path(key)))) {
                diets.add(key.substring("diet:".length()));
            }
        }
        return List.copyOf(diets);
    }

    private static String text(JsonNode node) {
        return node.isString() && !node.asString().isBlank() ? node.asString().trim() : null;
    }
}
